package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * One step of a flow. Steps run one after the other; a step sees the outputs of the steps before it.
 * The JSON {@code type} selects the kind of step.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ReportStep.class, name = ReportStep.TYPE),
        @JsonSubTypes.Type(value = MailStep.class, name = MailStep.TYPE)
})
public sealed interface FlowStep permits ReportStep, MailStep {

    /** Unique within the flow; later steps refer to this step's output by it. */
    String key();

    /** Null = the default of the step type. */
    Integer timeoutSeconds();

    /** Attempts including the first one; null = {@code flows.max-attempts}. */
    Integer maxAttempts();

    String typeName();

    Lane lane();
}
