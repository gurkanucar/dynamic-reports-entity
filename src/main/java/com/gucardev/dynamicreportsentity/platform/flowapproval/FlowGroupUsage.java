package com.gucardev.dynamicreportsentity.platform.flowapproval;

import com.gucardev.dynamicreportsentity.platform.approval.ApprovalGroupUsage;
import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowReferences;
import org.springframework.stereotype.Component;

/** Tells the approval module whether a flow's approval step uses a group (it then cannot be deleted). */
@Component
class FlowGroupUsage implements ApprovalGroupUsage {

    private final FlowReferences references;

    FlowGroupUsage(FlowReferences references) {
        this.references = references;
    }

    @Override
    public boolean usesGroup(long groupId) {
        return references.isReferenced(ApprovalStepType.APPROVAL_GROUP, Long.toString(groupId));
    }
}
