package com.gucardev.dynamicreportsentity.platform.flow.spi;

import java.time.Instant;
import java.util.Map;

/**
 * How a step attempt ended successfully.
 *
 * @param output    for later steps ({@code steps.<key>})
 * @param outcome   which edge to take ({@code true}, {@code approved} ...); null = the step's single way out
 * @param waitUntil set: the step now waits for something outside the engine (an approval) until this time at most;
 *                  it holds no thread and no lease meanwhile, and goes on through {@link WaitingSteps#resume}
 *                  (or with outcome {@code timeout} after this time)
 */
public record StepResult(Map<String, Object> output, String outcome, Instant waitUntil) {

    public static StepResult done(Map<String, Object> output) {
        return new StepResult(output, null, null);
    }

    public static StepResult outcome(Map<String, Object> output, String outcome) {
        return new StepResult(output, outcome, null);
    }

    public static StepResult waitUntil(Map<String, Object> output, Instant until) {
        return new StepResult(output, null, until);
    }
}
