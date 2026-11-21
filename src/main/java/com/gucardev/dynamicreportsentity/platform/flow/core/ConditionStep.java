package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowStep;
import com.gucardev.dynamicreportsentity.platform.flow.spi.Lane;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

/**
 * Branches on a JSON Logic {@code expression} evaluated over {@code {flow, run, trigger, steps}}: outcome
 * {@code true} or {@code false}. Without edges, {@code false} ends the run (a guard, e.g. "only mail when the dataset
 * has rows": {@code {">": [{"var": "steps.dataset.rowCount"}, 0]}}).
 */
public record ConditionStep(
        @NotBlank @Pattern(regexp = FlowStep.KEY_PATTERN, message = FlowStep.KEY_MESSAGE) String key,
        @NotNull Object expression,
        @Min(1) @Max(60) Integer timeoutSeconds,
        @Min(1) @Max(10) Integer maxAttempts) implements FlowStep {

    public static final String TYPE = "condition";

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

    @Override
    @JsonIgnore
    public boolean continuesInSequence(String outcome) {
        return "true".equals(outcome);
    }

    @Override
    @JsonIgnore
    public Set<String> outcomes() {
        return Set.of("true", "false");
    }
}
