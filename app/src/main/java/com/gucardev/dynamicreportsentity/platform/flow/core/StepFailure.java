package com.gucardev.dynamicreportsentity.platform.flow.core;

/**
 * How an attempt ended when it did not succeed.
 *
 * @param retryable another attempt may help (if any are left)
 * @param cancelled the run was cancelled: stop, no retry
 * @param release   the instance is shutting down: hand the step back without counting the attempt
 */
record StepFailure(String code, String message, boolean retryable, boolean cancelled, boolean release) {

    static StepFailure retryable(String code, String message) {
        return new StepFailure(code, message, true, false, false);
    }

    static StepFailure permanent(String code, String message) {
        return new StepFailure(code, message, false, false, false);
    }

    static StepFailure cancel() {
        return new StepFailure("RUN_CANCELLED", "The run was cancelled", false, true, false);
    }

    static StepFailure handBack() {
        return new StepFailure("INSTANCE_STOPPING", "The instance running the step is shutting down", true, false,
                true);
    }
}
