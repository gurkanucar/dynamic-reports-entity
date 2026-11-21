package com.gucardev.dynamicreportsentity.platform.approval;

/** A request was opened; members are notified after the commit (see {@link ApprovalNotifier}). */
record ApprovalRequested(long requestId, boolean notifyMembers) {
}
