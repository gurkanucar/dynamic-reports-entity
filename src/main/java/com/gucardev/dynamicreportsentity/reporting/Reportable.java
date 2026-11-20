package com.gucardev.dynamicreportsentity.reporting;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Makes a JPA entity available in the report builder, both as a report root and as a target of
 * navigations. Only fields annotated with {@link ReportField} become visible.
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
}
