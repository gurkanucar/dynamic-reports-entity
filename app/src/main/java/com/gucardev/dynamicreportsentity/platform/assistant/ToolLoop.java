package com.gucardev.dynamicreportsentity.platform.assistant;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tool-calling conversation every drafter has: send, run the tools the model asks for, send their answers back,
 * until a tool hands in a result, the model answers in plain text (a question for the user) or
 * {@code assistant.max-iterations} is used up.
 */
final class ToolLoop {

    /** What a tool call came to: an answer for the model, or the final result. */
    sealed interface Outcome permits ToolAnswer, Done {
    }

    record ToolAnswer(Object answer) implements Outcome {
    }

    record Done(SuggestionResult result) implements Outcome {
    }

    @FunctionalInterface
    interface Tools {
        /**
         * @param arguments the call's arguments (null if they were not JSON)
         * @param said      what the model wrote along with the call, if anything
         */
        Outcome call(String name, JsonNode arguments, String said, int iteration, String model);
    }

    static final JsonMapper JSON = JsonMapper.builder().build();

    private ToolLoop() {
    }

    static SuggestionResult run(ChatClient chat, AssistantProperties properties, List<Map<String, Object>> messages,
                                List<Map<String, Object>> schemas, Tools tools, String fallback, String gaveUp) {
        String model = null;
        for (int iteration = 1; iteration <= properties.maxIterations(); iteration++) {
            ChatClient.Reply reply = chat.complete(messages, schemas);
            model = reply.model() != null ? reply.model() : properties.model();
            if (reply.toolCalls().isEmpty()) {
                String text = reply.content() == null || reply.content().isBlank() ? fallback : reply.content();
                return SuggestionResult.message(text.strip(), iteration, model);
            }
            Map<String, Object> turn = new LinkedHashMap<>();
            turn.put("role", "assistant");
            turn.put("content", reply.content());
            turn.put("tool_calls", reply.toolCalls().stream().map(call -> Map.of("id", call.id(), "type", "function",
                    "function", Map.of("name", call.name(), "arguments", call.arguments()))).toList());
            messages.add(turn);
            for (ChatClient.ToolCall call : reply.toolCalls()) {
                Outcome outcome = tools.call(call.name(), arguments(call.arguments()),
                        reply.content() == null ? null : reply.content().strip(), iteration, model);
                if (outcome instanceof Done done) {
                    return done.result();
                }
                messages.add(Map.of("role", "tool", "tool_call_id", call.id(),
                        "content", JSON.writeValueAsString(((ToolAnswer) outcome).answer())));
            }
        }
        throw new AssistantException(422, "ASSISTANT_GAVE_UP", gaveUp);
    }

    static Map<String, Object> message(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    static Map<String, Object> function(String name, String description, Map<String, Object> parameters) {
        return Map.of("type", "function", "function",
                Map.of("name", name, "description", description, "parameters", parameters));
    }

    static String text(JsonNode arguments, String field) {
        JsonNode value = arguments == null ? null : arguments.get(field);
        return value == null || !value.isString() ? null : value.asString().strip();
    }

    private static JsonNode arguments(String arguments) {
        try {
            return arguments == null || arguments.isBlank() ? null : JSON.readTree(arguments);
        } catch (JacksonException e) {
            return null;
        }
    }
}
