package com.gucardev.dynamicreportsentity.platform.flowapproval;

import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowStep;

/** Approval steps for tests of other packages (the record's constructor is the adapter's business). */
public final class TestSteps {

    private TestSteps() {
    }

    public static FlowStep approval(String key) {
        return new ApprovalStep(key, 1L, "Approve?", null, null, null, null, null, null);
    }
}
