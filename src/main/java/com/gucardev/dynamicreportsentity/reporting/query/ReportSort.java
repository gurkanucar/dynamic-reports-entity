package com.gucardev.dynamicreportsentity.reporting.query;

/**
 * Sort by a field path, optionally aggregated or date-grouped (must then match a grouped column);
 * {@code direction} defaults to ascending.
 */
public record ReportSort(String field, Aggregate aggregate, Direction direction, DateGroup dateGroup) {

    public enum Direction { ASC, DESC }

    public ReportSort {
        direction = direction == null ? Direction.ASC : direction;
    }

    public ReportSort(String field, Aggregate aggregate, Direction direction) {
        this(field, aggregate, direction, null);
    }

    public static ReportSort asc(String field) {
        return new ReportSort(field, null, Direction.ASC, null);
    }

    public static ReportSort desc(String field) {
        return new ReportSort(field, null, Direction.DESC, null);
    }
}
