package com.gucardev.dynamicreportsentity.platform.flow.spi;

public enum TriggerType {
    MANUAL,
    SCHEDULE,
    WEBHOOK,
    KAFKA,
    /** An event of another module of the application, e.g. a survey response ({@link FlowEvents}). */
    EVENT
}
