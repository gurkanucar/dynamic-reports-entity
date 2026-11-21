package com.gucardev.dynamicreportsentity.platform.assistant;

import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyDefinition;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Designs surveys from a chat: questions, sections, follow-ups ({@code visibleWhen}) and rules. {@code check_survey}
 * runs the same checks as saving; the result is a draft the user applies in the survey builder (as one undo step)
 * and saves there.
 */
@Component
class SurveyAssistant implements Drafter {

    private static final List<Map<String, Object>> TOOLS = tools();

    private final ChatClient chat;
    private final SurveyService surveys;
    private final AssistantProperties properties;
    private final Clock clock;
    private final String prompt;

    SurveyAssistant(ChatClient chat, SurveyService surveys, AssistantProperties properties, Clock clock) {
        this.chat = chat;
        this.surveys = surveys;
        this.properties = properties;
        this.clock = clock;
        this.prompt = MailTemplateAssistant.resource("assistant/survey-system-prompt.md");
    }

    @Override
    public String kind() {
        return "survey";
    }

    @Override
    public SuggestionResult draft(List<ChatMessage> conversation, Object current, Actor viewer) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(ToolLoop.message("system", prompt.replace("{{TODAY}}", LocalDate.now(clock).toString())));
        if (current != null) {
            messages.add(ToolLoop.message("system", "Current survey (the user wants to change it):\n"
                    + ToolLoop.JSON.writeValueAsString(current)));
        }
        conversation.forEach(turn -> messages.add(ToolLoop.message(turn.role(), turn.content())));
        return ToolLoop.run(chat, properties, messages, TOOLS, (name, arguments, said, iteration, model) -> {
            switch (name) {
                case "check_survey" -> {
                    return new ToolLoop.ToolAnswer(check(arguments));
                }
                case "submit_survey" -> {
                    Map<String, Object> checked = check(arguments);
                    if (!Boolean.TRUE.equals(checked.get("valid"))) {
                        return new ToolLoop.ToolAnswer(checked);
                    }
                    String title = ToolLoop.text(arguments, "title");
                    Map<String, Object> draft = new LinkedHashMap<>();
                    draft.put("title", title);
                    draft.put("description", ToolLoop.text(arguments, "description"));
                    draft.put("definition", ToolLoop.JSON.convertValue(arguments.path("definition"), Map.class));
                    draft.put("assumptions", strings(arguments, "assumptions"));
                    draft.put("suggestions", strings(arguments, "suggestions"));
                    return new ToolLoop.Done(new SuggestionResult(said, title,
                            ToolLoop.text(arguments, "explanation"), null, List.of(), draft, iteration, model));
                }
                default -> {
                    return new ToolLoop.ToolAnswer(Map.of("error",
                            "Unknown tool '" + name + "'. Use check_survey or submit_survey."));
                }
            }
        }, "I could not design a survey from that. Could you tell me a little more about who answers it and why?",
                "The assistant could not write a valid survey in " + properties.maxIterations() + " attempts.");
    }

    /** The checks of saving, as {@code {valid, problems: ["definition.fields[2].options: ..."]}}. */
    private Map<String, Object> check(JsonNode arguments) {
        String title = ToolLoop.text(arguments, "title");
        List<String> problems = new ArrayList<>();
        if (title == null || title.isBlank()) {
            problems.add("title: required");
        }
        JsonNode definition = arguments.path("definition");
        if (!definition.isObject()) {
            problems.add("definition: required, an object with fields and rules");
        } else {
            try {
                for (FieldProblem problem : surveys.check(SurveyDefinition.from(
                        ToolLoop.JSON.convertValue(definition, Map.class)))) {
                    problems.add(problem.field() + ": " + problem.message());
                }
            } catch (RuntimeException e) {
                problems.add("definition: not in the expected shape (" + e.getMessage() + ")");
            }
        }
        return problems.isEmpty() ? Map.of("valid", true) : Map.of("valid", false, "problems", problems);
    }

    private static List<String> strings(JsonNode arguments, String field) {
        List<String> values = new ArrayList<>();
        arguments.path(field).forEach(node -> {
            if (node.isString() && !node.asString().isBlank()) {
                values.add(node.asString());
            }
        });
        return values;
    }

    private static List<Map<String, Object>> tools() {
        Map<String, Object> survey = new LinkedHashMap<>();
        survey.put("title", Map.of("type", "string"));
        survey.put("description", Map.of("type", "string"));
        survey.put("definition", Map.of("type", "object", "description",
                "{submitLabel, successMessage, fields: [...], rules: [...]}"));
        Map<String, Object> submit = new LinkedHashMap<>(survey);
        submit.put("explanation", Map.of("type", "string", "description", "For the user: what you made and why"));
        submit.put("assumptions", Map.of("type", "array", "items", Map.of("type", "string")));
        submit.put("suggestions", Map.of("type", "array", "items", Map.of("type", "string"),
                "description", "2-4 next steps written as commands"));
        return List.of(
                ToolLoop.function("check_survey", "Checks a draft survey like saving it would. Returns {valid} or "
                        + "{valid: false, problems}.", Map.of("type", "object", "properties", survey,
                        "required", List.of("title", "definition"))),
                ToolLoop.function("submit_survey", "Hands in the finished survey.", Map.of("type", "object",
                        "properties", submit, "required", List.of("title", "definition", "explanation"))));
    }
}
