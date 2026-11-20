package com.gucardev.dynamicreportsentity.platform.flow.core;

import java.util.EnumSet;
import java.util.Set;

public enum RunStatus {
    /** Waiting for a free slot on some instance. */
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public static final Set<RunStatus> ACTIVE = EnumSet.of(QUEUED, RUNNING);

    public boolean isFinished() {
        return !ACTIVE.contains(this);
    }
}
