package com.gucardev.dynamicreportsentity.platform.flowapproval;

import com.gucardev.dynamicreportsentity.platform.approval.ApprovalService;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepCheck;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepReference;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepType;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Checks approval steps; their group is a reference, so the group cannot be deleted while a flow uses it. */
@Component
class ApprovalStepType implements StepType<ApprovalStep> {

    static final String APPROVAL_GROUP = "approval-group";

    private final ApprovalService approvals;

    ApprovalStepType(ApprovalService approvals) {
        this.approvals = approvals;
    }

    @Override
    public String name() {
        return ApprovalStep.TYPE;
    }

    @Override
    public Class<ApprovalStep> stepClass() {
        return ApprovalStep.class;
    }

    @Override
    public void validate(ApprovalStep step, StepCheck check) {
        if (!approvals.groupExists(step.groupId())) {
            check.problem("groupId", "APPROVAL_GROUP_NOT_FOUND",
                    "Approval group " + step.groupId() + " does not exist.");
        }
        check.template("title", step.title());
        if (step.description() != null) {
            check.template("description", step.description());
        }
    }

    @Override
    public Set<StepReference> references(ApprovalStep step) {
        return Set.of(StepReference.of(APPROVAL_GROUP, step.groupId()));
    }
}
