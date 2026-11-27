package com.gucardev.dynamicreportsentity.platform.flow.spi;

import java.util.UUID;
/**
 * A waiting step stopped waiting without being resumed: its run was cancelled or its deadline passed. Lets the
 * module it waited for withdraw what it opened (e.g. an approval request). Delivered at least once.
 */
public record WaitingStepEnded(UUID stepRunId, String stepType, Reason reason) {

    public enum Reason { CANCELLED, TIMED_OUT }
}
