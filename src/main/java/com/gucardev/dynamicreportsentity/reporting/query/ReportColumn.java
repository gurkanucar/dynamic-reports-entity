package com.gucardev.dynamicreportsentity.reporting.query;

/**
 * A selected column.
 *
 * @param field     path from the root entity, e.g. {@code department.name}; may be omitted for {@code COUNT} (= COUNT(*))
 * @param aggregate optional aggregate; non-aggregated columns of a grouped report form the GROUP BY
 * @param label     optional header override
 * @param dateGroup optional period for date fields, e.g. {@code MONTH} → one row per month
 */
public record ReportColumn(String field, Aggregate aggregate, String label, DateGroup dateGroup) {

    public ReportColumn(String field, Aggregate aggregate, String label) {
        this(field, aggregate, label, null);
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
}
