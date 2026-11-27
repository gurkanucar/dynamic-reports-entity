package com.gucardev.dynamicreportsentity.platform.assistant;

import com.gucardev.entityreports.Dataset;
import com.gucardev.entityreports.DatasetExecutor;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The conversation with the model. The model gets the user's catalog and two tools: {@code validate_dataset} runs our
 * dataset builder on its draft as the requesting user (no query is executed) and {@code submit_dataset} hands in the
 * result, which is validated once more. Errors go back to the model until the definition passes or
 * {@code assistant.max-iterations} is used up. A plain-text answer is a clarifying question for the user.
 */
@Slf4j
@Component
class DatasetAssistant implements Drafter {

    private record Check(Dataset.Definition definition, List<Map<String, String>> columns, String error) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<Map<String, Object>> TOOLS = tools();

    private final ChatClient chat;
    private final CatalogDescriber catalog;
    private final DatasetExecutor datasets;
    private final Validator validator;
    private final AssistantProperties properties;
    private final Clock clock;
    private final String promptTemplate;

    DatasetAssistant(ChatClient chat, CatalogDescriber catalog, DatasetExecutor datasets, Validator validator,
                    AssistantProperties properties, Clock clock) {
        this.chat = chat;
        this.catalog = catalog;
        this.datasets = datasets;
        this.validator = validator;
        this.properties = properties;
        this.clock = clock;
        try {
            this.promptTemplate = new ClassPathResource("assistant/dataset-system-prompt.md").getContentAsString(UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String kind() {
        return "dataset";
    }

    @Override
    public SuggestionResult draft(List<ChatMessage> conversation, Object currentDefinition, Actor viewer) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", promptTemplate
                .replace("{{TODAY}}", LocalDate.now(clock).toString())
                .replace("{{CATALOG}}", catalog.describe(viewer))));
        if (currentDefinition != null) {
            messages.add(message("system", "Current dataset definition (the user wants to change it):\n"
                    + JSON.writeValueAsString(currentDefinition)));
        }
        conversation.forEach(turn -> messages.add(message(turn.role(), turn.content())));
        return ToolLoop.run(chat, properties, messages, TOOLS, (name, arguments, said, iteration, model) -> {
            switch (name) {
                case "validate_dataset" -> {
                    Check check = check(arguments == null ? null : arguments.get("definition"), viewer);
                    return new ToolLoop.ToolAnswer(check.error() == null
                            ? Map.of("valid", true, "columns", check.columns())
                            : Map.of("valid", false, "error", check.error()));
                }
                case "submit_dataset" -> {
                    Check check = check(arguments == null ? null : arguments.get("definition"), viewer);
                    if (check.error() == null) {
                        return new ToolLoop.Done(new SuggestionResult(said, ToolLoop.text(arguments, "title"),
                                ToolLoop.text(arguments, "explanation"), check.definition(), check.columns(), null,
                                iteration, model));
                    }
                    return new ToolLoop.ToolAnswer(Map.of("accepted", false, "error", check.error()));
                }
                default -> {
                    return new ToolLoop.ToolAnswer(Map.of("error",
                            "Unknown tool '" + name + "'. Use validate_dataset or submit_dataset."));
                }
            }
        }, "I could not build a dataset from that. Could you describe it differently?",
                "The assistant could not build a valid dataset in " + properties.maxIterations()
                        + " attempts. Try describing the dataset more precisely.");
    }

    /** The dataset builder's verdict on a draft, as the requesting user; nothing is executed. */
    private Check check(JsonNode draft, Actor viewer) {
        if (draft == null || !draft.isObject()) {
            return new Check(null, null, "Missing 'definition' object.");
        }
        Dataset.Definition definition;
        try {
            definition = JSON.treeToValue(draft, Dataset.Definition.class);
        } catch (JacksonException e) {
            return new Check(null, null, "The definition does not match the format: " + e.getOriginalMessage());
        }
        String violations = validator.validate(definition).stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .sorted().collect(Collectors.joining("; "));
        if (!violations.isEmpty()) {
            return new Check(null, null, violations);
        }
        try {
            Dataset.Query query = datasets.prepare(definition, viewer);
            List<Map<String, String>> columns = query.columns().stream()
                    .map(column -> Map.of("label", column.label(), "type", String.valueOf(column.type())))
                    .toList();
            return new Check(definition, columns, null);
        } catch (Dataset.InvalidException e) {
            return new Check(null, null, e.getMessage());
        } catch (Dataset.AccessDeniedException e) {
            return new Check(null, null, "Not allowed for this user: " + e.getMessage());
        }
    }

    private static Map<String, Object> message(String role, String content) {
        return ToolLoop.message(role, content);
    }

    /** Tool schemas; the definition grammar is in the system prompt (recursive conditions do not fit every API). */
    private static List<Map<String, Object>> tools() {
        Map<String, Object> definition = Map.of("type", "object",
                "description", "A dataset definition as described in the system prompt.",
                "properties", Map.of(
                        "root", Map.of("type", "string"),
                        "columns", Map.of("type", "array", "items", Map.of("type", "object")),
                        "where", Map.of("type", "object"),
                        "having", Map.of("type", "object"),
                        "sort", Map.of("type", "array", "items", Map.of("type", "object")),
                        "perRoot", Map.of("type", "boolean")),
                "required", List.of("root", "columns"));
        return List.of(
                function("validate_dataset", "Checks a draft dataset definition with the dataset builder, as the "
                                + "current user. Returns {valid, columns} or {valid: false, error}.",
                        Map.of("type", "object", "properties", Map.of("definition", definition),
                                "required", List.of("definition"))),
                function("submit_dataset", "Hands in the final dataset definition with a short title and an "
                                + "explanation for the user, both in the user's language.",
                        Map.of("type", "object", "properties", Map.of(
                                        "title", Map.of("type", "string"),
                                        "explanation", Map.of("type", "string"),
                                        "definition", definition),
                                "required", List.of("title", "explanation", "definition"))));
    }

    private static Map<String, Object> function(String name, String description, Map<String, Object> parameters) {
        return ToolLoop.function(name, description, parameters);
    }
}
