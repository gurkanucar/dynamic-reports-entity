package com.gucardev.dynamicreportsentity.platform.flow.core;

import java.time.Instant;
import java.util.Map;

/**
 * How a step attempt ended successfully.
 *
 * @param output    for later steps ({@code steps.<key>})
 * @param outcome   which edge to take ({@code true}, {@code approved} ...); null = the step's single way out
 * @param waitUntil set: the step now waits for something outside the engine (an approval) until this time at most;
 *                  it holds no thread and no lease meanwhile
 */
record StepResult(Map<String, Object> output, String outcome, Instant waitUntil) {

    static StepResult done(Map<String, Object> output) {
        return new StepResult(output, null, null);
    }

    static StepResult outcome(Map<String, Object> output, String outcome) {
        return new StepResult(output, outcome, null);
    }

    static StepResult waitUntil(Map<String, Object> output, Instant until) {
        return new StepResult(output, null, until);
    }
}
