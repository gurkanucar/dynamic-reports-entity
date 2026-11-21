package com.gucardev.dynamicreportsentity.platform.flow.spi;

import com.gucardev.dynamicreportsentity.platform.identity.Actor;

import java.util.Set;

/**
 * What a {@link StepType} needs to check one step of a flow being saved. Fields are relative to the step
 * ({@code "url"} becomes {@code steps[2].url}).
 */
public interface StepCheck {

    void problem(String field, String code, String message);

    /** Datasets {@code TEMPLATE_SYNTAX_ERROR} if the Mustache template does not compile. */
    void template(String field, String template);

    /** Datasets {@code INVALID_EXPRESSION} if the JSON Logic rule is not valid. */
    void expression(String field, Object rule);

    /** Who saves the flow. */
    Actor editor();

    /** Who the flow runs as; null if they no longer exist (then only the editor is checked). */
    Actor owner();

    /** Keys of the steps that may run before this one. */
    Set<String> stepsBefore();

    /** Whether the step with this key stores a file ({@link StepType#producesFiles()}). */
    boolean producesFiles(String key);
}
