package com.gucardev.dynamicreportsentity.platform.reporting;

/**
 * "Rows of {@code entity} are visible only if {@code field} matches {@code value}".
 * <p>
 * The engine applies it to every occurrence of the entity in a report, and to every entity that
 * declares it as {@link ReportReference#owner() owner}. On the report root it filters rows
 * (WHERE); on joined data it hides the related rows (JOIN ... ON), so they show up as empty.
 *
 * @param entity logical entity name, e.g. {@code Personnel}
 * @param field  Java field name, need not be a report field, e.g. {@code departmentId}
 * @param value  a single value, or a collection for "any of"
 */
public record RowRestriction(String entity, String field, Object value) {
}
