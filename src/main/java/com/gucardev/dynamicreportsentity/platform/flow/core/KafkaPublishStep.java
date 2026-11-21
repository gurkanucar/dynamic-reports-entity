package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Publishes an event to Kafka. {@code topic} must match {@code flows.kafka.allowed-topics}; {@code payload} and
 * {@code messageKey} are Mustache templates over {@code {flow, run, trigger, steps}} (the payload JSON-escaped).
 * Delivery is at least once: a retried step may publish again, consumers should de-duplicate by the
 * {@code flow-run-id} and {@code flow-step} headers. Output: {@code topic}, {@code partition}, {@code offset}.
 */
public record KafkaPublishStep(
        @NotBlank @Pattern(regexp = ReportStep.KEY_PATTERN, message = ReportStep.KEY_MESSAGE) String key,
        @NotBlank @Size(max = 249) @Pattern(regexp = "[a-zA-Z0-9._-]+") String topic,
        @Size(max = 500) String messageKey,
        @NotBlank @Size(max = 100_000) String payload,
        @Min(1) @Max(120) Integer timeoutSeconds,
        @Min(1) @Max(10) Integer maxAttempts) implements FlowStep {

    public static final String TYPE = "kafka";

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
}
