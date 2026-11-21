package com.gucardev.dynamicreportsentity.platform.flow.spi;

/**
 * Something outside the flow a step uses, e.g. {@code ("mail-template", "3")}; see {@link FlowReferences}.
 */
public record StepReference(String kind, String id) {

    public static StepReference of(String kind, long id) {
        return new StepReference(kind, Long.toString(id));
    }
}
