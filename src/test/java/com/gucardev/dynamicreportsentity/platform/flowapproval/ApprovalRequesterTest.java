package com.gucardev.dynamicreportsentity.platform.flowapproval;

import com.gucardev.dynamicreportsentity.platform.flow.spi.ClaimedStep;
import com.gucardev.dynamicreportsentity.platform.flow.spi.TriggerType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Who counts as asking for an approval (the four-eyes rule): depends on how the run started. */
class ApprovalRequesterTest {

    private static ClaimedStep run(TriggerType trigger, String triggeredBy, Object payload) {
        return new ClaimedStep(1, "t", 1, 1, 1, "Flow", "owner", trigger, triggeredBy, Instant.now(),
                TestSteps.approval("approval"), Duration.ofSeconds(30), Map.of(), payload);
    }

    @Test
    void theOwnerAsksForManualAndScheduledRunsNotForOtherSystems() {
        assertThat(ApprovalStepHandler.onBehalfOf(run(TriggerType.MANUAL, "alice", null))).isEqualTo("owner");
        assertThat(ApprovalStepHandler.onBehalfOf(run(TriggerType.SCHEDULE, "schedule:3", null))).isEqualTo("owner");
        assertThat(ApprovalStepHandler.onBehalfOf(run(TriggerType.WEBHOOK, "webhook:3", Map.of()))).isEqualTo("webhook:3");
        assertThat(ApprovalStepHandler.onBehalfOf(run(TriggerType.KAFKA, "kafka:2", Map.of()))).isEqualTo("kafka:2");
    }

    @Test
    void aSurveyResponseIsAskedForByItsRespondent() {
        assertThat(ApprovalStepHandler.onBehalfOf(run(TriggerType.EVENT, "event:5", Map.of("startedBy", "bob"))))
                .isEqualTo("bob");
        assertThat(ApprovalStepHandler.onBehalfOf(run(TriggerType.EVENT, "event:5", Map.of()))).isEqualTo("event:5");
    }
}
