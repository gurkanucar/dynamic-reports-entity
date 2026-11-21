package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Set;

/**
 * One step (node) of a flow. Without edges the steps run one after the other; with edges they form a graph and a
 * step's outcome picks the next one (e.g. a condition's {@code true}/{@code false}). A step sees the outputs of the
 * steps that ran before it ({@code steps.<key>}) and the trigger's payload ({@code trigger}).
 * The JSON {@code type} selects the kind of step.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ReportStep.class, name = ReportStep.TYPE),
        @JsonSubTypes.Type(value = MailStep.class, name = MailStep.TYPE),
        @JsonSubTypes.Type(value = ConditionStep.class, name = ConditionStep.TYPE),
        @JsonSubTypes.Type(value = FilterStep.class, name = FilterStep.TYPE),
        @JsonSubTypes.Type(value = ApprovalStep.class, name = ApprovalStep.TYPE),
        @JsonSubTypes.Type(value = WebhookStep.class, name = WebhookStep.TYPE),
        @JsonSubTypes.Type(value = KafkaPublishStep.class, name = KafkaPublishStep.TYPE)
})
public sealed interface FlowStep
        permits ReportStep, MailStep, ConditionStep, FilterStep, ApprovalStep, WebhookStep, KafkaPublishStep {

    /** Unique within the flow; later steps refer to this step's output by it. */
    String key();

    /** Null = the default of the step type. */
    Integer timeoutSeconds();

    /** Attempts including the first one; null = {@code flows.max-attempts}. */
    Integer maxAttempts();

    String typeName();

    Lane lane();

    /** Outcomes edges may branch on ({@code edge.when}); empty = the step has a single way out. */
    default Set<String> outcomes() {
        return Set.of();
    }
}
