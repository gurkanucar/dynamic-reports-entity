package com.gucardev.dynamicreportsentity.reporting;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Keeps a field out of reports on an entity with {@link Reportable#includeAllFields()} = true.
 * (Without includeAllFields, fields are hidden by default and this annotation is not needed.)
 */
@Documented
@Retention(RUNTIME)
@Target(FIELD)
public @interface ReportHidden {
}
