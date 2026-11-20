package com.gucardev.dynamicreportsentity.platform.reporting.query;

/** Aggregate functions. Using any of them in a column turns the report into a grouped one. */
public enum Aggregate {
    COUNT("Count"),
    COUNT_DISTINCT("Distinct count"),
    SUM("Sum"),
    AVG("Average"),
    MIN("Minimum"),
    MAX("Maximum"),
    /**
     * Distinct values as a sorted array, e.g. a person's skills: {@code ["Java", "SQL"]}. Turns a
     * one-to-many relation into a nested list on the parent row instead of one row per child.
     * Only usable as a column (not in having or sort).
     */
    LIST("List");

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
