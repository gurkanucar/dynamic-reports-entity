package com.gucardev.dynamicreportsentity.platform.dataset.catalog;

/**
 * How a generated dataset query uses a column; the input for index advice.
 * Collected while the SQL is built, so it costs nothing extra at run time.
 */
public record ColumnUsage(String table, String column, Kind kind) {

    public enum Kind {
        /** Join key of a navigation. */
        JOIN,
        /** Plain comparison in WHERE (=, <, IN, BETWEEN, IS NULL, STARTS_WITH, ...): a B-tree index helps. */
        FILTER,
        /** Filter through a date function (YEAR, MONTH, ...): a plain index on the column is not used. */
        FILTER_EXPRESSION,
        /** LIKE '%...%': a B-tree index is not used; needs a trigram / full-text index. */
        FILTER_CONTAINS,
        /** ORDER BY of a detail (non-grouped) dataset: an index can avoid sorting. */
        SORT
    }
}
