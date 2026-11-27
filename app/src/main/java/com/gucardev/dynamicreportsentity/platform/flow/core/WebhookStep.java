package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowStep;
import com.gucardev.dynamicreportsentity.platform.flow.spi.Lane;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Calls another system over HTTP. Only hosts in {@code flows.webhook.allowed-hosts} may be called (no requests into
 * the company network the owner did not mean). {@code body} is a Mustache template over
 * {@code {flow, run, trigger, steps}}; values are JSON-escaped when {@code contentType} is JSON.
 * 2xx succeeds; 408, 429 and 5xx are retried; other answers fail the step. Output: {@code status}, {@code body}.
 */
public record WebhookStep(
        @NotBlank @Pattern(regexp = FlowStep.KEY_PATTERN, message = FlowStep.KEY_MESSAGE) String key,
        @NotBlank @Size(max = 2000) String url,
        @Pattern(regexp = "POST|PUT|PATCH|GET|DELETE") String method,
        @Size(max = 20) Map<@NotBlank @Size(max = 100) String, @Size(max = 2000) String> headers,
        @Size(max = 100_000) String body,
        @Size(max = 100) String contentType,
        @Min(1) @Max(300) Integer timeoutSeconds,
        @Min(1) @Max(10) Integer maxAttempts) implements FlowStep {

    public static final String TYPE = "webhook";

    public WebhookStep {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    @Override
    @JsonIgnore
    public String typeName() {
        return TYPE;
    }

    @Override
    @JsonIgnore
    public Lane lane() {
        return Lane.LIGHT;
    }

    @JsonIgnore
    public String methodOrDefault() {
        return method == null ? "POST" : method;
    }

    @JsonIgnore
    public String contentTypeOrDefault() {
        return contentType == null ? "application/json" : contentType;
    }
}
