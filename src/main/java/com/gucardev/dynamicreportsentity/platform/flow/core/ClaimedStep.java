package com.gucardev.dynamicreportsentity.platform.flow.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Everything a step handler needs, read in the claim transaction.
 *
 * @param token           claim token: proves this attempt still owns the step
 * @param previousOutputs outputs of the completed earlier steps, by step key
 */
record ClaimedStep(long stepRunId, String token, int attempt, long runId, long flowId, String flowName,
                   String runAs, TriggerType trigger, String triggeredBy, Instant runStartedAt, FlowStep step,
                   Duration timeout, Map<String, Map<String, Object>> previousOutputs) {
}
