package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.reporting.query.QueryHandle;

import java.time.Instant;

/**
 * Executes one kind of step. Runs on a virtual thread, outside any transaction; may be stopped through
 * {@link StepContext#queryHandle()} (database work) or by interrupting the thread (other work).
 */
interface StepHandler {

    /** The {@link FlowStep#typeName()} this handler executes. */
    String type();

    /** Whether stopping the step should interrupt its thread (database work is stopped through the handle). */
    default boolean interruptible() {
        return true;
    }

    /** @return the step's output for later steps (JSON-compatible values), its outcome, or that it waits */
    StepResult execute(StepContext context) throws Exception;

    record StepContext(ClaimedStep step, QueryHandle queryHandle, Instant deadline) {
    }
}
