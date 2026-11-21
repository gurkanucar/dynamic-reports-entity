package com.gucardev.dynamicreportsentity.platform.flow.spi;

import java.util.Set;

/**
 * A kind of step, registered as a bean. Always present (even if its {@link StepHandler} is switched off), so stored
 * flows using it can still be read.
 *
 * @param <S> the step's record
 */
public interface StepType<S extends FlowStep> {

    /** The JSON {@code type}; equals {@link FlowStep#typeName()} of its steps. */
    String name();

    Class<S> stepClass();

    /** Checks a step when a flow is saved; report problems through {@code check} (every problem at once). */
    default void validate(S step, StepCheck check) {
    }

    /** What the step refers to in other modules (a mail template, an approval group), so they are not deleted. */
    default Set<StepReference> references(S step) {
        return Set.of();
    }

    /** Whether the step stores a file later steps may attach ({@code steps.<key>.artifactId}). */
    default boolean producesFiles() {
        return false;
    }
}
