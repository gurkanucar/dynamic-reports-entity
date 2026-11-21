package com.gucardev.dynamicreportsentity.platform.flowapproval;

import com.gucardev.dynamicreportsentity.platform.approval.ApprovalDecided;
import com.gucardev.dynamicreportsentity.platform.approval.ApprovalService;
import com.gucardev.dynamicreportsentity.platform.flow.spi.WaitingStepEnded;
import com.gucardev.dynamicreportsentity.platform.flow.spi.WaitingSteps;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carries events between the two modules, each after its transaction committed and at least once:
 * <ul>
 *   <li>a decision resumes the waiting step ({@code approved}, {@code rejected} or {@code timeout});</li>
 *   <li>a step that stopped waiting (run cancelled, engine deadline) withdraws its request.</li>
 * </ul>
 * Both sides ignore repeats. A decision that arrives before the step started waiting fails here and is delivered
 * again later ({@code IncompleteEventRetry}).
 */
@Component
class ApprovalBridge {

    private final WaitingSteps steps;
    private final ApprovalService approvals;

    ApprovalBridge(WaitingSteps steps, ApprovalService approvals) {
        this.steps = steps;
        this.approvals = approvals;
    }

    @ApplicationModuleListener
    void on(ApprovalDecided decided) {
        if (!ApprovalStepHandler.SOURCE_TYPE.equals(decided.sourceType())) {
            return;
        }
        String outcome = switch (decided.outcome()) {
            case APPROVED -> "approved";
            case REJECTED -> "rejected";
            case TIMED_OUT -> "timeout";
        };
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("decision", outcome);
        output.put("decidedBy", decided.decidedBy());
        output.put("comment", decided.comment());
        output.put("decidedAt", decided.decidedAt() == null ? null : decided.decidedAt().toString());
        String message = decided.decidedBy() == null ? null : decided.decidedBy()
                + (decided.comment() == null ? "" : ": " + decided.comment());
        steps.resume(Long.parseLong(decided.sourceId()), outcome, output, switch (decided.outcome()) {
            case APPROVED -> "approval_approved";
            case REJECTED -> "approval_rejected";
            case TIMED_OUT -> "approval_timed_out";
        }, message);
    }

    @ApplicationModuleListener
    void on(WaitingStepEnded ended) {
        if (ApprovalStep.TYPE.equals(ended.stepType())) {
            approvals.cancel(ApprovalStepHandler.SOURCE_TYPE, Long.toString(ended.stepRunId()));
        }
    }
}
