package com.gucardev.entityreports;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Declares that a plain ID field points at the primary key of another {@link DatasetEntity} entity.
 * <p>
 * Instead of {@code @ManyToOne Personnel manager} the entity keeps only {@code Long managerId}.
 * The owning module stays free of compile-time dependencies; the dataset engine turns the
 * reference into a <em>named navigation</em> ({@code manager}), so datasets address related data
 * by path, e.g. {@code manager.department.name}. Naming every relation is what makes
 * self-references and multiple relations between the same two entities unambiguous.
 * <p>
 * How each relation kind is expressed:
 * <ul>
 *   <li>many-to-one: the reference itself ({@code TrainingRecord.personnelId → personnel})</li>
 *   <li>one-to-many: {@link #inverse()} ({@code Personnel.trainingRecords})</li>
 *   <li>one-to-one: {@link #oneToOne()} = true, the inverse is then single-valued</li>
 *   <li>self-reference: {@link #target()} is the entity itself ({@code Personnel.managerId})</li>
 *   <li>many-to-many: a junction entity with two references; with or without extra fields</li>
 * </ul>
 */
@Documented
@Retention(RUNTIME)
@Target(FIELD)
public @interface DatasetReference {

    /** {@link DatasetEntity#name() Logical name} of the referenced entity. */
    String target();

    /** Navigation name from this entity. Defaults to the field name without "Id" ({@code managerId → manager}). */
    String name() default "";

    /** Label of the navigation; defaults to the target entity's label. */
    String label() default "";

    /** Navigation name from the target back to this entity. Empty = not navigable in that direction. */
    String inverse() default "";

    /** Label of the inverse navigation; defaults to this entity's label. */
    String inverseLabel() default "";

    /** At most one row of this entity per target row (unique column): the inverse is to-one, not to-many. */
    boolean oneToOne() default false;

    /**
     * Rows of this entity belong to the referenced row and inherit its row-level restrictions
     * (see {@link Dataset.RowPolicy}). E.g. a training record is visible only if its person is.
     */
    boolean owner() default false;
}
