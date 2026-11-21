package com.gucardev.dynamicreportsentity.platform.flow.spi;

import java.util.Map;

/**
 * Lets a step that waits ({@link StepResult#waitUntil}) go on once its outside event happened, e.g. an approval
 * decision. Idempotent: resuming a step that already went on, or was cancelled, does nothing.
 */
public interface WaitingSteps {

    /**
     * @param outcome   which edge to take
     * @param output    added to the step's output
     * @param eventType recorded in the run's history (e.g. {@code approval_approved})
     * @return false if the step was no longer waiting (already resumed, timed out or cancelled)
     * @throws NotWaitingYetException if the step has not started waiting yet; try again later
     */
    boolean resume(long stepRunId, String outcome, Map<String, Object> output, String eventType, String message);

    /** The step is still running the attempt that starts the wait; the event came first. */
    class NotWaitingYetException extends RuntimeException {
        public NotWaitingYetException(long stepRunId) {
            super("Flow step " + stepRunId + " is not waiting yet");
        }
    }
}
