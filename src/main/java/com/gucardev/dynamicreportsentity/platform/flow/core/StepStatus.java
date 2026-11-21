package com.gucardev.dynamicreportsentity.platform.flow.core;

public enum StepStatus {
    /** Waiting for a slot; also after a failed attempt, until {@code availableAt}. */
    READY,
    /** Claimed by an instance under a lease. */
    RUNNING,
    /** Waits for something outside the engine (an approval) until {@code availableAt}; no lease, no thread. */
    WAITING,
    COMPLETED,
    FAILED,
    CANCELLED
}
