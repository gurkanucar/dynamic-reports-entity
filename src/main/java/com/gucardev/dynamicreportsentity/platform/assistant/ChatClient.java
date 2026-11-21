package com.gucardev.dynamicreportsentity.platform.assistant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal client for the OpenAI-compatible {@code POST /chat/completions} with tool calling, which OpenRouter, vLLM,
 * Ollama and most gateways speak. Deliberately small: no SDK, no provider lock-in.
 */
@Slf4j
@Component
class ChatClient {

    /** A function call the model asked for; {@code arguments} is a JSON string. */
    record ToolCall(String id, String name, String arguments) {
    }

    /** The model's answer: text, tool calls or both. */
    record Reply(String content, List<ToolCall> toolCalls, String model) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AssistantProperties properties;
    private final RestClient http;

    ChatClient(AssistantProperties properties) {
        this.properties = properties;
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        requests.setReadTimeout(properties.requestTimeout());
        this.http = RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requests)
                .defaultHeader("Authorization", "Bearer " + (properties.apiKey() == null ? "" : properties.apiKey()))
                .defaultHeader("X-Title", properties.appName())
                .build();
    }

    Reply complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("messages", messages);
        body.put("tools", tools);
        body.put("tool_choice", "auto");
        body.put("temperature", properties.temperature());
        body.put("usage", Map.of("include", true));   // OpenRouter: dataset tokens and cost; others ignore it
        String reasoning = properties.reasoning() == null ? "" : properties.reasoning().strip().toLowerCase(java.util.Locale.ROOT);
        if (reasoning.equals("off") || reasoning.equals("none")) {
            body.put("reasoning", Map.of("enabled", false));   // OpenRouter; sent only when configured
        } else if (!reasoning.isEmpty()) {
            body.put("reasoning", Map.of("effort", reasoning));
        }
        String json = JSON.writeValueAsString(body);
        try {
            return parse(send(json));
        } catch (AssistantException e) {
            if (!"ASSISTANT_CONNECTION_LOST".equals(e.code())) {
                throw e;
            }
            log.info("Assistant connection lost ({}); trying once more", e.getMessage());
            return parse(send(json));   // a dropped connection is usually gone on the second try
        }
    }

    /**
     * One request. A model that is still writing when {@code assistant.request-timeout} runs out is reported as
     * ASSISTANT_TIMEOUT (not retried: it would only time out again); a connection the network dropped as
     * ASSISTANT_CONNECTION_LOST, which {@link #complete} retries once.
     */
    private String send(String json) {
        try {
            return http.post().uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw upstreamError(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            if (timedOut(e)) {
                throw timeout();
            }
            throw new AssistantException(502, "ASSISTANT_UNAVAILABLE",
                    "The AI service could not be reached: " + e.getMostSpecificCause().getMessage());
        } catch (RestClientException e) {
            // the answer was cut off while it was being read: the read timeout closed the stream, or the network did
            if (timedOut(e)) {
                throw timeout();
            }
            throw new AssistantException(502, "ASSISTANT_CONNECTION_LOST",
                    "The connection to the AI service broke off: " + e.getMostSpecificCause().getMessage());
        }
    }

    private AssistantException timeout() {
        return new AssistantException(504, "ASSISTANT_TIMEOUT", "The AI service did not answer within "
                + properties.requestTimeout().toSeconds() + " s. Ask for a smaller change, or raise "
                + "assistant.request-timeout (ASSISTANT_REQUEST_TIMEOUT).");
    }

    /** Read timeouts show up as HttpTimeoutException, or as a stream the timeout handler cancelled. */
    static boolean timedOut(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String message = String.valueOf(cause.getMessage()).toLowerCase(java.util.Locale.ROOT);
            if (cause instanceof java.net.http.HttpTimeoutException || cause instanceof java.net.SocketTimeoutException
                    || message.contains("timed out") || message.contains("cancelled")) {
                return true;
            }
        }
        return false;
    }

    private Reply parse(String response) {
        JsonNode root = JSON.readTree(response);
        if (root.has("error")) {   // some gateways answer errors with HTTP 200
            throw upstreamError(root.path("error").path("code").asInt(502), response);
        }
        JsonNode message = root.path("choices").path(0).path("message");
        if (message.isMissingNode()) {
            throw new AssistantException(502, "ASSISTANT_BAD_RESPONSE", "The AI service sent no answer");
        }
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            JsonNode function = call.path("function");
            JsonNode arguments = function.path("arguments");
            calls.add(new ToolCall(call.path("id").asString(), function.path("name").asString(),
                    arguments.isString() ? arguments.asString() : arguments.toString()));
        }
        JsonNode usage = root.path("usage");
        if (!usage.isMissingNode()) {
            log.info("Assistant call to {}: {} prompt + {} completion tokens{}", root.path("model").asString("?"),
                    usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt(),
                    usage.has("cost") ? ", cost " + usage.path("cost").asString() : "");
        }
        String content = message.path("content").isString() ? message.path("content").asString() : null;
        return new Reply(content, calls, root.path("model").asString(null));
    }

    private static AssistantException upstreamError(int status, String body) {
        String detail = errorMessage(body);
        return switch (status) {
            case 401, 403 -> new AssistantException(502, "ASSISTANT_UNAUTHORIZED",
                    "The AI service rejected the API key: " + detail);
            case 402 -> new AssistantException(502, "ASSISTANT_NO_CREDIT",
                    "The AI account has no credit left: " + detail);
            case 429 -> new AssistantException(503, "ASSISTANT_RATE_LIMITED",
                    "The AI service is rate limiting; try again shortly: " + detail);
            case 400, 404, 422 -> new AssistantException(502, "ASSISTANT_BAD_REQUEST",
                    "The AI service refused the request (model id, tool support?): " + detail);
            default -> new AssistantException(502, "ASSISTANT_UNAVAILABLE", "The AI service failed: " + detail);
        };
    }

    private static String errorMessage(String body) {
        try {
            JsonNode message = JSON.readTree(body).path("error").path("message");
            if (message.isString()) {
                return truncate(message.asString());
            }
        } catch (RuntimeException ignored) {
            // not JSON
        }
        return truncate(body);
    }

    private static String truncate(String text) {
        return text == null ? "" : text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
