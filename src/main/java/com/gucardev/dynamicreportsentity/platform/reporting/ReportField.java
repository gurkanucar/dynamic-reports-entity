package com.gucardev.dynamicreportsentity.platform.reporting;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Makes a field of a {@link Reportable} entity selectable, filterable and sortable in reports.
 * A field without this annotation can never appear in a report.
 */
@Documented
@Retention(RUNTIME)
@Target(FIELD)
public @interface ReportField {

    /** Business-friendly label shown in the report builder. */
    String label();

    /**
     * Authority required to use this field anywhere in a report (select, filter or sort).
     * Users without it do not even see the field in the catalog. Empty means no restriction.
     */
    String authority() default "";
}
