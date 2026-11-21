package com.gucardev.dynamicreportsentity.platform.assistant;

import com.gucardev.dynamicreportsentity.platform.api.ApiException;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import com.gucardev.dynamicreportsentity.platform.notification.MailTemplateService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Writes mail templates (subject and HTML body with a {@code <style>} block) from a chat, so business users get a
 * good-looking mail without writing HTML or CSS. {@code preview_template} renders the draft with sample data (syntax
 * errors go back to the model); the result is a draft the user reviews and saves in the template editor.
 */
@Component
class MailTemplateAssistant implements Drafter {

    private static final List<Map<String, Object>> TOOLS = tools();

    private final ChatClient chat;
    private final MailTemplateService templates;
    private final AssistantProperties properties;
    private final Clock clock;
    private final String prompt;

    MailTemplateAssistant(ChatClient chat, MailTemplateService templates, AssistantProperties properties, Clock clock) {
        this.chat = chat;
        this.templates = templates;
        this.properties = properties;
        this.clock = clock;
        this.prompt = resource("assistant/mail-template-system-prompt.md");
    }

    @Override
    public String kind() {
        return "mail-template";
    }

    @Override
    public SuggestionResult draft(List<ChatMessage> conversation, Object current, Actor viewer) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(ToolLoop.message("system", prompt.replace("{{TODAY}}", LocalDate.now(clock).toString())
                .replace("{{DATASET}}", datasetSection(current))));
        if (current != null) {
            messages.add(ToolLoop.message("system", "Current template (the user wants to change it):\n"
                    + ToolLoop.JSON.writeValueAsString(current)));
        }
        conversation.forEach(turn -> messages.add(ToolLoop.message(turn.role(), turn.content())));
        return ToolLoop.run(chat, properties, messages, TOOLS, (name, arguments, said, iteration, model) -> {
            switch (name) {
                case "preview_template" -> {
                    return new ToolLoop.ToolAnswer(preview(arguments));
                }
                case "submit_template" -> {
                    Map<String, Object> preview = preview(arguments);
                    if (!Boolean.TRUE.equals(preview.get("valid"))) {
                        return new ToolLoop.ToolAnswer(preview);
                    }
                    String templateName = ToolLoop.text(arguments, "name");
                    if (templateName == null || templateName.isBlank()) {
                        return new ToolLoop.ToolAnswer(Map.of("accepted", false, "error", "name is required"));
                    }
                    Map<String, Object> draft = new LinkedHashMap<>();
                    draft.put("name", templateName);
                    draft.put("description", ToolLoop.text(arguments, "description"));
                    draft.put("subject", ToolLoop.text(arguments, "subject"));
                    draft.put("bodyHtml", ToolLoop.text(arguments, "bodyHtml"));
                    return new ToolLoop.Done(new SuggestionResult(said, templateName,
                            ToolLoop.text(arguments, "explanation"), null, List.of(), draft, iteration, model));
                }
                default -> {
                    return new ToolLoop.ToolAnswer(Map.of("error",
                            "Unknown tool '" + name + "'. Use preview_template or submit_template."));
                }
            }
        }, "I could not write a template from that. Could you describe the e-mail differently?",
                "The assistant could not write a valid template in " + properties.maxIterations() + " attempts.");
    }

    /**
     * The dataset the user picked for the template ({@code current.sampleDataset: {name, label, columns}}): the model
     * then writes for its columns, e.g. a sentence about the first column's values.
     */
    private static String datasetSection(Object current) {
        if (!(current instanceof Map<?, ?> map) || !(map.get("sampleDataset") instanceof Map<?, ?> dataset)) {
            return "";
        }
        return "# The data of this mail\nThe template is for the dataset `" + dataset.get("name") + "`"
                + (dataset.get("label") == null ? "" : " (" + dataset.get("label") + ")")
                + ", read by a step with the key `dataset`. Its columns: " + dataset.get("columns")
                + ". `steps.dataset.columns` lists these titles; write texts that fit this data.\n\n";
    }

    /** Renders the draft with sample data; errors name the line. */
    private Map<String, Object> preview(JsonNode arguments) {
        String subject = ToolLoop.text(arguments, "subject");
        String body = ToolLoop.text(arguments, "bodyHtml");
        if (subject == null || subject.isBlank() || body == null || body.isBlank()) {
            return Map.of("valid", false, "error", "subject and bodyHtml are required");
        }
        try {
            Map<String, String> rendered = templates.preview(subject, body, null);
            return Map.of("valid", true, "renderedSubject", rendered.get("subject"),
                    "renderedBodyLength", rendered.get("bodyHtml").length());
        } catch (ApiException e) {
            return Map.of("valid", false, "error", e.getMessage(), "problems", e.errors() == null ? List.of()
                    : e.errors().stream().map(problem -> problem.field() + ": " + problem.message()).toList());
        }
    }

    private static List<Map<String, Object>> tools() {
        Map<String, Object> template = Map.of(
                "subject", Map.of("type", "string", "description", "Plain text with Mustache placeholders"),
                "bodyHtml", Map.of("type", "string", "description", "HTML with one <style> block and Mustache"));
        Map<String, Object> submit = new LinkedHashMap<>(template);
        submit.put("name", Map.of("type", "string", "description", "Short template name"));
        submit.put("description", Map.of("type", "string", "description", "What the mail is for, one sentence"));
        submit.put("explanation", Map.of("type", "string", "description", "For the user: what you made"));
        return List.of(
                ToolLoop.function("preview_template", "Renders a draft template with sample data. Returns "
                        + "{valid, renderedSubject} or {valid: false, error}.", Map.of("type", "object",
                        "properties", template, "required", List.of("subject", "bodyHtml"))),
                ToolLoop.function("submit_template", "Hands in the finished template.", Map.of("type", "object",
                        "properties", submit, "required", List.of("name", "subject", "bodyHtml", "explanation"))));
    }

    static String resource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
