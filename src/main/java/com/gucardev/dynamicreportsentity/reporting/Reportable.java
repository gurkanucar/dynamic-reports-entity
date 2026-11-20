package com.gucardev.dynamicreportsentity.reporting;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Makes a JPA entity available in the report builder, both as a report root and as a target of
 * navigations.
 * <p>
 * Two styles:
 * <ul>
 *   <li><b>opt-in</b> (default): only fields annotated with {@link ReportField} are visible. A new column
 *       added later stays hidden until someone decides to expose it. Safest for sensitive data.</li>
 *   <li><b>opt-out</b> ({@link #includeAllFields()} = true): every plain field is visible with a label
 *       derived from its name ({@code validityMonths} → "Validity Months"); {@link ReportHidden} keeps a
 *       field out and {@link ReportField} still overrides label or authority. Less boilerplate, but a new
 *       column is reportable as soon as it is added.</li>
 * </ul>
 * The single-column primary key is always reportable as {@code id}; reference columns are reached via
 * their navigation ({@code department.id}).
 */
@Documented
@Retention(RUNTIME)
@Target(TYPE)
public @interface Reportable {

    /**
     * Logical name of the entity, used as report root and by {@link ReportReference#target()}.
     * Defaults to the class' simple name.
     */
    String name() default "";

    /** Business-friendly label shown in the report builder. */
    String label();

    /** Expose every plain field without {@link ReportField}, except those marked {@link ReportHidden}. */
    boolean includeAllFields() default false;
}
