package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

/**
 * Branches on a JSON Logic {@code expression} evaluated over {@code {flow, run, trigger, steps}}: outcome
 * {@code true} or {@code false}. Without edges, {@code false} ends the run (a guard, e.g. "only mail when the report
 * has rows": {@code {">": [{"var": "steps.report.rowCount"}, 0]}}).
 */
public record ConditionStep(
        @NotBlank @Pattern(regexp = ReportStep.KEY_PATTERN, message = ReportStep.KEY_MESSAGE) String key,
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
    public Set<String> outcomes() {
        return Set.of("true", "false");
    }
}
