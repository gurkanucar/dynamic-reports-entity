package com.gucardev.dynamicreportsentity.platform.dataset.catalog;

/**
 * A named way to get from one entity to another, derived from a {@code @DatasetReference}.
 * Joining it produces {@code joined.joinColumn = current.parentColumn}.
 *
 * @param name         navigation name used in paths, e.g. {@code manager}
 * @param label        business label, e.g. {@code Manager}
 * @param targetEntity logical name of the entity reached
 * @param toMany       whether one current row may match many target rows (fan-out)
 * @param joinColumn   column on the target entity
 * @param parentColumn column on the current entity
 */
public record Navigation(String name, String label, String targetEntity, boolean toMany,
                         String joinColumn, String parentColumn) {
}
