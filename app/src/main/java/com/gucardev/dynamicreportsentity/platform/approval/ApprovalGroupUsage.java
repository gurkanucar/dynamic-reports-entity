package com.gucardev.dynamicreportsentity.platform.approval;

import java.util.UUID;
/**
 * Implemented by modules that refer to approval groups (e.g. flows' approval steps), so a group in use cannot be
 * deleted without this module knowing who uses it.
 */
public interface ApprovalGroupUsage {

    boolean usesGroup(UUID groupId);
}
