package com.gucardev.dynamicreportsentity.platform.flow.spi;

/**
 * How other modules start flows with their own events, without the flow module knowing them: a module publishes
 * under a {@code source} such as {@code survey:42}, and every enabled {@code event} trigger listening on that source
 * starts a run with the payload as {@code trigger}. Call it from an event listener, after the module's transaction
 * committed.
 */
public interface FlowEvents {

    /** {@code <module>:<id>}, e.g. {@code survey:42}. */
    String SOURCE_PATTERN = "[a-z][a-z0-9-]{0,30}:[A-Za-z0-9._-]{1,100}";

    /**
     * @param eventKey unique per event of this source: a redelivery with the same key starts no second run
     * @return how many runs started
     */
    int publish(String source, String eventKey, Object payload);
}
