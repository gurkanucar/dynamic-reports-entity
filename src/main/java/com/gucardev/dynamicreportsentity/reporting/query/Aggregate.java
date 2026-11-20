package com.gucardev.dynamicreportsentity.reporting.query;

/** Aggregate functions. Using any of them in a column turns the report into a grouped one. */
public enum Aggregate {
    COUNT("Count"),
    COUNT_DISTINCT("Distinct count"),
    SUM("Sum"),
    AVG("Average"),
    MIN("Minimum"),
    MAX("Maximum");

    private final String label;

    Aggregate(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    /** Aggregates whose result changes when rows are duplicated by a one-to-many join. */
    boolean isSensitiveToDuplicates() {
        return this == COUNT || this == SUM || this == AVG;
    }
}
