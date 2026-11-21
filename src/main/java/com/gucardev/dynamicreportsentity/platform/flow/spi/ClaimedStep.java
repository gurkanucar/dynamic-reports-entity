package com.gucardev.dynamicreportsentity.platform.flow.spi;

import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Everything a step handler needs, read in the claim transaction.
 *
 * @param token           claim token: proves this attempt still owns the step
 * @param previousOutputs outputs of the completed earlier steps, by step key
 * @param triggerPayload  the webhook body or Kafka message that started the run (JSON value), else null
 */
public record ClaimedStep(UUID stepRunId, String token, int attempt, UUID runId, UUID flowId, String flowName,
                   String runAs, TriggerType trigger, String triggeredBy, Instant runStartedAt, FlowStep step,
                   Duration timeout, Map<String, Map<String, Object>> previousOutputs, Object triggerPayload) {
}
