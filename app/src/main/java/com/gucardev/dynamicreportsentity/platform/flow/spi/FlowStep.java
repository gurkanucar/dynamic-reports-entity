package com.gucardev.dynamicreportsentity.platform.flow.spi;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.annotation.JsonTypeIdResolver;

import java.util.Set;

/**
 * One step (node) of a flow. Without edges the steps run one after the other; with edges they form a graph and a
 * step's outcome picks the next one (e.g. a condition's {@code true}/{@code false}). A step sees the outputs of the
 * steps that ran before it ({@code steps.<key>}) and the trigger's payload ({@code trigger}).
 * <p>
 * The JSON {@code type} selects the kind of step; the kinds are whatever {@link StepType} beans exist, so a module
 * adds one without changing this interface.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, property = "type")
@JsonTypeIdResolver(StepTypeIdResolver.class)
public interface FlowStep {

    /** Step keys: what later steps write as {@code steps.<key>}. */
    String KEY_PATTERN = "[a-z][a-z0-9_]{0,39}";
    String KEY_MESSAGE = "must be 1-40 lower case letters, digits or underscores, starting with a letter";

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

    /**
     * In a flow without edges, whether the run goes on to the next step after this outcome. Steps with outcomes
     * override it to name the "yes" one (a condition's {@code true}); by default every outcome goes on.
     */
    default boolean continuesInSequence(String outcome) {
        return true;
    }
}
