package com.gucardev.dynamicreportsentity.reporting.query;

/** Filter operators the report builder can use. Nothing outside this list ever reaches the SQL. */
public enum FilterOperator {
    EQ("="),
    NOT_EQ("<>"),
    LT("<"),
    LTE("<="),
    GT(">"),
    GTE(">="),
    /** value: [from, to], both inclusive */
    BETWEEN(null),
    /** value: list of values */
    IN(null),
    NOT_IN(null),
    /** Text contains the value; wildcards in the value are escaped. */
    CONTAINS(null),
    STARTS_WITH(null),
    /** No value. */
    IS_NULL(null),
    IS_NOT_NULL(null),
    /** Business-friendly: date between today and today + N days, e.g. "renewal due within 30 days". */
    NEXT_N_DAYS(null),
    /** Date between today - N days and today. */
    LAST_N_DAYS(null),
    /** At least one related row (via "relation") matches the optional "match" condition. */
    EXISTS(null),
    /** No related row matches, e.g. people WITHOUT a Fire Safety training. */
    NOT_EXISTS(null);

    private final String comparison;

    FilterOperator(String comparison) {
        this.comparison = comparison;
    }

    /** SQL comparison operator, or {@code null} for operators with their own rendering. */
    String comparison() {
        return comparison;
    }
}
