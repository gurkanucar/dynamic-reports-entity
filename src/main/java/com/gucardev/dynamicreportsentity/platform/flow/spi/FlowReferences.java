package com.gucardev.dynamicreportsentity.platform.flow.spi;

/** Whether any flow uses something ({@link StepType#references}), for modules guarding their own deletes. */
public interface FlowReferences {

    boolean isReferenced(String kind, String id);
}
