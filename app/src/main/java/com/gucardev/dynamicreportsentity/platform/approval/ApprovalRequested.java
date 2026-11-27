package com.gucardev.dynamicreportsentity.platform.approval;

import java.util.UUID;
/** A request was opened; members are notified after the commit (see {@link ApprovalNotifier}). */
record ApprovalRequested(UUID requestId, boolean notifyMembers) {
}
