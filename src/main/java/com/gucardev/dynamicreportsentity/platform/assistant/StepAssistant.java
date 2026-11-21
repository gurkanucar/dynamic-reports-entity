package com.gucardev.dynamicreportsentity.platform.assistant;

import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Changes ONE step of a flow from a chat ("add alice in cc", "only above 100.000 TL"): the flow on screen comes along
 * as context, the model hands in the new step, which is checked inside the whole flow (same rules as saving). The
 * draft is {@code {step, newTemplates}}; problems of other steps are not this step's business and are ignored.
 */
@Component
class StepAssistant implements Drafter {

    private static final String INSTRUCTIONS = """

            # Your task now: change ONE step
            The user selected the step with the key `%s` in the flow below and asks for a change to it. Change only
            that step: keep its `key` and `type` unless the user asks otherwise, keep every setting they did not
            mention. Steps before it are available as `steps.<key>` (in templates, conditions, bodies).
            Call `validate_step` with the new step and fix every problem, then `submit_step` with a one or two
            sentence explanation in the user's language. A mail step may use `templateRef` with `newTemplates`
            (as in submit_flow) when the user wants a different mail text and no listed template fits.
            If the request is about other steps or the whole flow, say so in ONE short sentence (no tool call).

            The flow:
            %s
            """;

    private static final List<Map<String, Object>> TOOLS = tools();

    private final ChatClient chat;
    private final FlowAssistant flows;
    private final AssistantProperties properties;

    StepAssistant(ChatClient chat, FlowAssistant flows, AssistantProperties properties) {
        this.chat = chat;
        this.flows = flows;
        this.properties = properties;
    }

    @Override
    public String kind() {
        return "step";
    }

    @Override
    public SuggestionResult draft(List<ChatMessage> conversation, Object current, Actor viewer) {
        JsonNode context = ToolLoop.JSON.valueToTree(current);
        String stepKey = context == null ? null : context.path("stepKey").asString(null);
        JsonNode flow = context == null ? null : context.get("flow");
        if (stepKey == null || flow == null || !flow.path("steps").isArray()) {
            throw new AssistantException(422, "STEP_CONTEXT_REQUIRED",
                    "Send the flow on screen as current.flow and the selected step as current.stepKey.");
        }
        int index = indexOf(flow, stepKey);
        if (index < 0) {
            throw new AssistantException(422, "STEP_CONTEXT_REQUIRED", "The flow has no step '" + stepKey + "'.");
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(ToolLoop.message("system", flows.systemPrompt(viewer)
                + INSTRUCTIONS.formatted(stepKey, ToolLoop.JSON.writeValueAsString(flow))));
        conversation.forEach(turn -> messages.add(ToolLoop.message(turn.role(), turn.content())));
        return ToolLoop.run(chat, properties, messages, TOOLS, (name, arguments, said, iteration, model) -> {
            switch (name) {
                case "validate_step", "submit_step" -> {
                    if (arguments == null || !arguments.path("step").isObject()) {
                        return new ToolLoop.ToolAnswer(Map.of("valid", false, "problems", List.of("Missing 'step'.")));
                    }
                    List<String> problems = problemsOfStep(flow, index, arguments, viewer);
                    if (!problems.isEmpty()) {
                        return new ToolLoop.ToolAnswer(Map.of("valid", false, "problems", problems));
                    }
                    if (name.equals("validate_step")) {
                        return new ToolLoop.ToolAnswer(Map.of("valid", true));
                    }
                    Map<String, Object> draft = new LinkedHashMap<>();
                    draft.put("step", ToolLoop.JSON.treeToValue(arguments.get("step"), Map.class));
                    JsonNode templates = arguments.get("newTemplates");
                    draft.put("newTemplates", templates == null || !templates.isArray() ? List.of()
                            : ToolLoop.JSON.treeToValue(templates, List.class));
                    return new ToolLoop.Done(new SuggestionResult(said, stepKey,
                            ToolLoop.text(arguments, "explanation"), null, List.of(), draft, iteration, model));
                }
                default -> {
                    return new ToolLoop.ToolAnswer(Map.of("error",
                            "Unknown tool '" + name + "'. Use validate_step or submit_step."));
                }
            }
        }, "Bu adımda neyi değiştireyim?",
                "The assistant could not change the step in " + properties.maxIterations() + " attempts.");
    }

    /** The flow with the new step in place, checked like saving it; only this step's (and graph) problems count. */
    private List<String> problemsOfStep(JsonNode flow, int index, JsonNode arguments, Actor viewer) {
        ObjectNode changed = (ObjectNode) flow.deepCopy();
        ((ArrayNode) changed.get("steps")).set(index, arguments.get("step").deepCopy());
        if (!changed.has("name")) {
            changed.put("name", "Flow");
        }
        ObjectNode check = ToolLoop.JSON.createObjectNode();
        check.set("flow", changed);
        if (arguments.has("newTemplates")) {
            check.set("newTemplates", arguments.get("newTemplates"));
        }
        String prefix = "steps[" + index + "]";
        return flows.check(check, viewer).stream()
                .filter(problem -> problem.startsWith(prefix) || !problem.startsWith("steps["))
                .filter(problem -> !problem.startsWith("name"))
                .toList();
    }

    private static int indexOf(JsonNode flow, String key) {
        JsonNode steps = flow.path("steps");
        for (int i = 0; i < steps.size(); i++) {
            if (key.equals(steps.get(i).path("key").asString(null))) {
                return i;
            }
        }
        return -1;
    }

    private static List<Map<String, Object>> tools() {
        Map<String, Object> step = Map.of("type", "object", "description", "The whole changed step (with type and key)");
        Map<String, Object> newTemplates = Map.of("type", "array", "items", Map.of("type", "object", "properties",
                Map.of("ref", Map.of("type", "string"), "name", Map.of("type", "string"),
                        "subject", Map.of("type", "string"), "bodyHtml", Map.of("type", "string")),
                "required", List.of("ref", "name", "subject", "bodyHtml")));
        return List.of(
                ToolLoop.function("validate_step", "Checks the changed step inside the flow. Returns {valid} or "
                                + "{valid: false, problems}.", Map.of("type", "object", "properties",
                        Map.of("step", step, "newTemplates", newTemplates), "required", List.of("step"))),
                ToolLoop.function("submit_step", "Hands in the changed step.", Map.of("type", "object", "properties",
                        Map.of("step", step, "newTemplates", newTemplates, "explanation", Map.of("type", "string")),
                        "required", List.of("step", "explanation"))));
    }
}
