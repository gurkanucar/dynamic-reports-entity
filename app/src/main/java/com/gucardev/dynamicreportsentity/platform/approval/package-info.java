/**
 * Approval module: groups of people who decide (managed in the app), requests waiting for one of them, decisions,
 * deadlines and notifications. Usable by any module: {@link com.gucardev.dynamicreportsentity.platform.approval.ApprovalService#open}
 * opens a request for a "source" (e.g. a flow step) and an
 * {@link com.gucardev.dynamicreportsentity.platform.approval.ApprovalDecided} event datasets the outcome. Knows nothing
 * about flows.
 */
@ApplicationModule(id = "approval", displayName = "Approvals", allowedDependencies = {"api", "identity", "notification"})
package com.gucardev.dynamicreportsentity.platform.approval;

import org.springframework.modulith.ApplicationModule;
