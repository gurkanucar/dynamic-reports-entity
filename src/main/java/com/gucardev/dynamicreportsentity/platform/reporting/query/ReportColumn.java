package com.gucardev.dynamicreportsentity.platform.reporting.query;

import jakarta.validation.Valid;

import java.util.List;

/**
 * A selected column.
 *
 * @param field     path from the root entity, e.g. {@code department.name}; may be omitted for {@code COUNT} (= COUNT(*)).
 *                  For a LIST of objects it names the relation, e.g. {@code trainingRecords}.
 * @param aggregate optional aggregate; non-aggregated columns of a grouped report form the GROUP BY
 * @param label     optional header override
 * @param dateGroup optional period for date fields, e.g. {@code MONTH} → one row per month
 * @param fields    LIST of objects: field paths relative to the relation, e.g. {@code ["training.name", "score"]}
 * @param where     per-root aggregates: filters the related rows, relative to the relation, e.g. only FAILED records
 * @param limit     LIST: max elements (default 100, at most 1000); longer lists are cut and reported as truncated
 * @param order     LIST: element order, ascending by default
 */
public record ReportColumn(String field, Aggregate aggregate, String label, DateGroup dateGroup,
                           List<String> fields, @Valid ReportCondition where, Integer limit,
                           ReportSort.Direction order) {

    public ReportColumn(String field, Aggregate aggregate, String label) {
        this(field, aggregate, label, null, null, null, null, null);
    }

    public ReportColumn(String field, Aggregate aggregate, String label, DateGroup dateGroup) {
        this(field, aggregate, label, dateGroup, null, null, null, null);
    }

    public static ReportColumn of(String field) {
        return new ReportColumn(field, null, null, null);
    }

    public static ReportColumn of(String field, DateGroup dateGroup) {
        return new ReportColumn(field, null, null, dateGroup);
    }

    public static ReportColumn of(Aggregate aggregate, String field) {
        return new ReportColumn(field, aggregate, null, null);
    }

    public static ReportColumn count() {
        return new ReportColumn(null, Aggregate.COUNT, null, null);
    }

    /** LIST of objects: one object per related row with the given fields. */
    public static ReportColumn objects(String relation, String... fields) {
        return new ReportColumn(relation, Aggregate.LIST, null, null, List.of(fields), null, null, null);
    }

    public ReportColumn where(ReportCondition childFilter) {
        return new ReportColumn(field, aggregate, label, dateGroup, fields, childFilter, limit, order);
    }

    public ReportColumn limit(int maxElements) {
        return new ReportColumn(field, aggregate, label, dateGroup, fields, where, maxElements, order);
    }

    public ReportColumn order(ReportSort.Direction direction) {
        return new ReportColumn(field, aggregate, label, dateGroup, fields, where, limit, direction);
    }
}
