/**
 * Approval steps for flows: plugs the approval module into the flow engine through its SPI. The step opens an
 * approval request for its step run and waits; the {@link com.gucardev.dynamicreportsentity.platform.approval.ApprovalDecided}
 * event resumes it; a cancelled or timed-out wait withdraws the request. Neither module knows the other.
 */
@ApplicationModule(id = "flow-approval", displayName = "Flow approval steps",
        allowedDependencies = {"flow :: spi", "approval", "notification"})
package com.gucardev.dynamicreportsentity.platform.flowapproval;

import org.springframework.modulith.ApplicationModule;
