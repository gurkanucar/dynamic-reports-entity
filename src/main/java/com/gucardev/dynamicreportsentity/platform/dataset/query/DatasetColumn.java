package com.gucardev.dynamicreportsentity.platform.dataset.query;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.Valid;

import java.util.List;

/**
 * A selected column.
 *
 * @param field     path from the root entity, e.g. {@code department.name}; may be omitted for {@code COUNT} (= COUNT(*)).
 *                  For a LIST of objects it names the relation, e.g. {@code trainingRecords}.
 * @param aggregate optional aggregate; non-aggregated columns of a grouped dataset form the GROUP BY
 * @param label     optional header override
 * @param dateGroup optional period for date fields, e.g. {@code MONTH} → one row per month
 * @param fields    LIST of objects: field paths relative to the relation, e.g. {@code ["training.name", "score"]}
 * @param where     per-root aggregates: filters the related rows, relative to the relation, e.g. only FAILED records
 * @param limit     LIST: max elements (default 100, at most 1000); longer lists are cut and reported as truncated
 * @param order     LIST: element order, ascending by default
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatasetColumn(String field, Aggregate aggregate, String label, DateGroup dateGroup,
                           List<String> fields, @Valid DatasetCondition where, Integer limit,
                           DatasetSort.Direction order) {

    public DatasetColumn(String field, Aggregate aggregate, String label) {
        this(field, aggregate, label, null, null, null, null, null);
    }

    public DatasetColumn(String field, Aggregate aggregate, String label, DateGroup dateGroup) {
        this(field, aggregate, label, dateGroup, null, null, null, null);
    }

    public static DatasetColumn of(String field) {
        return new DatasetColumn(field, null, null, null);
    }

    public static DatasetColumn of(String field, DateGroup dateGroup) {
        return new DatasetColumn(field, null, null, dateGroup);
    }

    public static DatasetColumn of(Aggregate aggregate, String field) {
        return new DatasetColumn(field, aggregate, null, null);
    }

    public static DatasetColumn count() {
        return new DatasetColumn(null, Aggregate.COUNT, null, null);
    }

    /** LIST of objects: one object per related row with the given fields. */
    public static DatasetColumn objects(String relation, String... fields) {
        return new DatasetColumn(relation, Aggregate.LIST, null, null, List.of(fields), null, null, null);
    }

    public DatasetColumn where(DatasetCondition childFilter) {
        return new DatasetColumn(field, aggregate, label, dateGroup, fields, childFilter, limit, order);
    }

    public DatasetColumn limit(int maxElements) {
        return new DatasetColumn(field, aggregate, label, dateGroup, fields, where, maxElements, order);
    }

    public DatasetColumn order(DatasetSort.Direction direction) {
        return new DatasetColumn(field, aggregate, label, dateGroup, fields, where, limit, direction);
    }
}
