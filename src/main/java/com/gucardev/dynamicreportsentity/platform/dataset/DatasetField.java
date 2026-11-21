package com.gucardev.dynamicreportsentity.platform.dataset;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Makes a field of a {@link DatasetEntity} entity selectable, filterable and sortable in datasets.
 * A field without this annotation can never appear in a dataset.
 */
@Documented
@Retention(RUNTIME)
@Target(FIELD)
public @interface DatasetField {

    /** Business-friendly label shown in the dataset builder. */
    String label();

    /**
     * Authority required to use this field anywhere in a dataset (select, filter or sort).
     * Users without it do not even see the field in the catalog. Empty means no restriction.
     */
    String authority() default "";
}
