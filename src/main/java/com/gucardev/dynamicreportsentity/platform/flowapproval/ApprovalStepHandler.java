package com.gucardev.dynamicreportsentity.platform.flowapproval;

import com.gucardev.dynamicreportsentity.platform.approval.ApprovalException;
import com.gucardev.dynamicreportsentity.platform.approval.ApprovalRequest;
import com.gucardev.dynamicreportsentity.platform.approval.ApprovalService;
import com.gucardev.dynamicreportsentity.platform.approval.NewApproval;
import com.gucardev.dynamicreportsentity.platform.flow.spi.ClaimedStep;
import com.gucardev.dynamicreportsentity.platform.flow.spi.RunData;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepContext;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepException;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepHandler;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepResult;
import com.gucardev.dynamicreportsentity.platform.notification.TemplateRenderer;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Opens an approval request for the step run (a retried attempt gets the same one) and lets the step wait without a
 * thread or lease. The approval module owns the deadline; the engine's own deadline is a safety net an hour later.
 */
@Component
class ApprovalStepHandler implements StepHandler {

    static final String SOURCE_TYPE = "flow-step";
    private static final Duration SAFETY_MARGIN = Duration.ofHours(1);

    private final ApprovalService approvals;
    private final TemplateRenderer renderer;
    private final Clock clock;

    ApprovalStepHandler(ApprovalService approvals, TemplateRenderer renderer, Clock clock) {
        this.approvals = approvals;
        this.renderer = renderer;
        this.clock = clock;
    }

    @Override
    public String type() {
        return ApprovalStep.TYPE;
    }

    @Override
    public StepResult execute(StepContext context) {
        ClaimedStep claimed = context.step();
        ApprovalStep step = (ApprovalStep) claimed.step();
        Map<String, Object> data = RunData.of(claimed, clock);
        Instant now = clock.instant();
        ApprovalRequest request;
        try {
            request = approvals.open(new NewApproval(SOURCE_TYPE, Long.toString(claimed.stepRunId()),
                    claimed.flowName() + " · çalışma #" + claimed.runId(), "/flows/" + claimed.flowId(),
                    step.groupId(), renderer.renderText(step.title(), data),
                    step.description() == null ? null : renderer.renderPlain(step.description(), data),
                    claimed.triggeredBy(), claimed.runAs(), Boolean.TRUE.equals(step.allowSelfApproval()),
                    !Boolean.FALSE.equals(step.notifyMembers()),
                    now.plus(Duration.ofHours(step.timeoutHoursOrDefault()))));
        } catch (ApprovalException e) {
            throw new StepException(e.code(), e.getMessage(), false);
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("requestId", request.getId());
        output.put("groupId", request.getGroupId());
        output.put("groupName", request.getGroupName());
        output.put("title", request.getTitle());
        output.put("dueAt", request.getDueAt().toString());
        return StepResult.waitUntil(output, request.getDueAt().plus(SAFETY_MARGIN));
    }
}
