package com.gucardev.dynamicreportsentity.platform.flow.spi;

/**
 * Executes one kind of step, registered as a bean. Runs on a virtual thread, outside any transaction; may be stopped
 * through {@link StepContext#onStop} (e.g. database work) or by interrupting the thread (other work). A step type
 * without a handler (switched off on this installation) fails with {@code STEP_TYPE_UNAVAILABLE}.
 */
public interface StepHandler {

    /** The {@link FlowStep#typeName()} this handler executes. */
    String type();

    /** Whether stopping the step should interrupt its thread (database work is stopped through {@code onStop}). */
    default boolean interruptible() {
        return true;
    }

    /** @return the step's output for later steps (JSON-compatible values), its outcome, or that it waits */
    StepResult execute(StepContext context) throws Exception;
}
