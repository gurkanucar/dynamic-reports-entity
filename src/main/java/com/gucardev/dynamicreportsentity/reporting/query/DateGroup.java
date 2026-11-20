package com.gucardev.dynamicreportsentity.reporting.query;

import com.gucardev.dynamicreportsentity.reporting.catalog.FieldType;

/**
 * Groups a date field into periods. Two flavours:
 * <ul>
 *   <li>timeline: {@link #YEAR}, {@link #QUARTER}, {@link #MONTH}, {@link #DAY} – each period is distinct
 *       ("2025-03" ≠ "2026-03"). QUARTER/MONTH return the first day of the period as a DATE so they sort
 *       chronologically; the UI formats them (e.g. "Mar 2025").</li>
 *   <li>seasonality: {@link #QUARTER_OF_YEAR}, {@link #MONTH_OF_YEAR} – a number that ignores the year
 *       (all Marches together).</li>
 * </ul>
 * The SQL ({@code EXTRACT}, {@code DATE_TRUNC}, {@code CAST}) works on both H2 and PostgreSQL.
 */
public enum DateGroup {
    YEAR("Year", FieldType.NUMBER, "EXTRACT(YEAR FROM %s)"),
    QUARTER("Quarter", FieldType.DATE, "CAST(DATE_TRUNC('QUARTER', %s) AS DATE)"),
    MONTH("Month", FieldType.DATE, "CAST(DATE_TRUNC('MONTH', %s) AS DATE)"),
    DAY("Day", FieldType.DATE, "CAST(%s AS DATE)"),
    QUARTER_OF_YEAR("Quarter of year", FieldType.NUMBER, "EXTRACT(QUARTER FROM %s)"),
    MONTH_OF_YEAR("Month of year", FieldType.NUMBER, "EXTRACT(MONTH FROM %s)");

    private final String label;
    private final FieldType resultType;
    private final String template;

    DateGroup(String label, FieldType resultType, String template) {
        this.label = label;
        this.resultType = resultType;
        this.template = template;
    }

    String label() {
        return label;
    }

    FieldType resultType() {
        return resultType;
    }

    /** {@code expression} is always a catalog column ({@code t0.hire_date}), never user input. */
    String apply(String expression) {
        return template.formatted(expression);
    }
}
