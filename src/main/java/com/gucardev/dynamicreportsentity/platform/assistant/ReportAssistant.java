package com.gucardev.dynamicreportsentity.platform.assistant;

import com.gucardev.dynamicreportsentity.platform.reporting.ReportAccessDeniedException;
import com.gucardev.dynamicreportsentity.platform.reporting.ReportViewer;
import com.gucardev.dynamicreportsentity.platform.reporting.query.InvalidReportException;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ReportDefinition;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ReportExecutor;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ReportQuery;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The conversation with the model. The model gets the user's catalog and two tools: {@code validate_report} runs our
 * report builder on its draft as the requesting user (no query is executed) and {@code submit_report} hands in the
 * result, which is validated once more. Errors go back to the model until the definition passes or
 * {@code assistant.max-iterations} is used up. A plain-text answer is a clarifying question for the user.
 */
@Slf4j
@Component
class ReportAssistant {

    private record Check(ReportDefinition definition, List<Map<String, String>> columns, String error) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<Map<String, Object>> TOOLS = tools();

    private final ChatClient chat;
    private final CatalogDescriber catalog;
    private final ReportExecutor reports;
    private final Validator validator;
    private final AssistantProperties properties;
    private final Clock clock;
    private final String promptTemplate;

    ReportAssistant(ChatClient chat, CatalogDescriber catalog, ReportExecutor reports, Validator validator,
                    AssistantProperties properties, Clock clock) {
        this.chat = chat;
        this.catalog = catalog;
        this.reports = reports;
        this.validator = validator;
        this.properties = properties;
        this.clock = clock;
        try {
            this.promptTemplate = new ClassPathResource("assistant/report-system-prompt.md").getContentAsString(UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    SuggestionResult suggest(List<ChatMessage> conversation, Object currentDefinition, ReportViewer viewer) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", promptTemplate
                .replace("{{TODAY}}", LocalDate.now(clock).toString())
                .replace("{{CATALOG}}", catalog.describe(viewer))));
        if (currentDefinition != null) {
            messages.add(message("system", "Current report definition (the user wants to change it):\n"
                    + JSON.writeValueAsString(currentDefinition)));
        }
        conversation.forEach(turn -> messages.add(message(turn.role(), turn.content())));

        String model = null;
        for (int iteration = 1; iteration <= properties.maxIterations(); iteration++) {
            ChatClient.Reply reply = chat.complete(messages, TOOLS);
            model = reply.model() != null ? reply.model() : properties.model();
            if (reply.toolCalls().isEmpty()) {
                String text = reply.content() == null || reply.content().isBlank()
                        ? "I could not build a report from that. Could you describe it differently?" : reply.content();
                return new SuggestionResult(text.strip(), null, null, null, List.of(), iteration, model);
            }
            Map<String, Object> assistantTurn = new LinkedHashMap<>();
            assistantTurn.put("role", "assistant");
            assistantTurn.put("content", reply.content());
            assistantTurn.put("tool_calls", reply.toolCalls().stream().map(call -> Map.of("id", call.id(),
                    "type", "function", "function", Map.of("name", call.name(), "arguments", call.arguments()))).toList());
            messages.add(assistantTurn);

            for (ChatClient.ToolCall call : reply.toolCalls()) {
                JsonNode arguments = parseArguments(call.arguments());
                String toolResult;
                switch (call.name()) {
                    case "validate_report" -> {
                        Check check = check(arguments == null ? null : arguments.get("definition"), viewer);
                        toolResult = check.error() == null
                                ? JSON.writeValueAsString(Map.of("valid", true, "columns", check.columns()))
                                : JSON.writeValueAsString(Map.of("valid", false, "error", check.error()));
                    }
                    case "submit_report" -> {
                        Check check = check(arguments == null ? null : arguments.get("definition"), viewer);
                        if (check.error() == null) {
                            return new SuggestionResult(reply.content() == null ? null : reply.content().strip(),
                                    text(arguments, "title"), text(arguments, "explanation"), check.definition(),
                                    check.columns(), iteration, model);
                        }
                        toolResult = JSON.writeValueAsString(Map.of("accepted", false, "error", check.error()));
                    }
                    default -> toolResult = JSON.writeValueAsString(Map.of("error",
                            "Unknown tool '" + call.name() + "'. Use validate_report or submit_report."));
                }
                messages.add(Map.of("role", "tool", "tool_call_id", call.id(), "content", toolResult));
            }
        }
        throw new AssistantException(422, "ASSISTANT_GAVE_UP", "The assistant could not build a valid report in "
                + properties.maxIterations() + " attempts. Try describing the report more precisely.");
    }

    /** The report builder's verdict on a draft, as the requesting user; nothing is executed. */
    private Check check(JsonNode draft, ReportViewer viewer) {
        if (draft == null || !draft.isObject()) {
            return new Check(null, null, "Missing 'definition' object.");
        }
        ReportDefinition definition;
        try {
            definition = JSON.treeToValue(draft, ReportDefinition.class);
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
            ReportQuery query = reports.prepare(definition, viewer);
            List<Map<String, String>> columns = query.columns().stream()
                    .map(column -> Map.of("label", column.label(), "type", String.valueOf(column.type())))
                    .toList();
            return new Check(definition, columns, null);
        } catch (InvalidReportException e) {
            return new Check(null, null, e.getMessage());
        } catch (ReportAccessDeniedException e) {
            return new Check(null, null, "Not allowed for this user: " + e.getMessage());
        }
    }

    private static JsonNode parseArguments(String arguments) {
        try {
            return arguments == null || arguments.isBlank() ? null : JSON.readTree(arguments);
        } catch (JacksonException e) {
            return null;
        }
    }

    private static String text(JsonNode arguments, String field) {
        JsonNode value = arguments.get(field);
        return value == null || !value.isString() ? null : value.asString().strip();
    }

    private static Map<String, Object> message(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    /** Tool schemas; the definition grammar is in the system prompt (recursive conditions do not fit every API). */
    private static List<Map<String, Object>> tools() {
        Map<String, Object> definition = Map.of("type", "object",
                "description", "A report definition as described in the system prompt.",
                "properties", Map.of(
                        "root", Map.of("type", "string"),
                        "columns", Map.of("type", "array", "items", Map.of("type", "object")),
                        "where", Map.of("type", "object"),
                        "having", Map.of("type", "object"),
                        "sort", Map.of("type", "array", "items", Map.of("type", "object")),
                        "perRoot", Map.of("type", "boolean")),
                "required", List.of("root", "columns"));
        return List.of(
                function("validate_report", "Checks a draft report definition with the report builder, as the "
                                + "current user. Returns {valid, columns} or {valid: false, error}.",
                        Map.of("type", "object", "properties", Map.of("definition", definition),
                                "required", List.of("definition"))),
                function("submit_report", "Hands in the final report definition with a short title and an "
                                + "explanation for the user, both in the user's language.",
                        Map.of("type", "object", "properties", Map.of(
                                        "title", Map.of("type", "string"),
                                        "explanation", Map.of("type", "string"),
                                        "definition", definition),
                                "required", List.of("title", "explanation", "definition"))));
    }

    private static Map<String, Object> function(String name, String description, Map<String, Object> parameters) {
        return Map.of("type", "function", "function",
                Map.of("name", name, "description", description, "parameters", parameters));
    }
}
