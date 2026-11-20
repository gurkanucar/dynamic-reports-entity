package com.gucardev.dynamicreportsentity.reporting.query;

import com.gucardev.dynamicreportsentity.reporting.catalog.ColumnUsage;

import java.util.List;

/**
 * Generated SQL. Identifiers come only from the catalog and generated aliases; every user value
 * is a {@code ?} parameter.
 *
 * @param columns         output columns, in the same order as the SELECT list
 * @param countSql        {@code SELECT COUNT(*)} over the same rows (no ORDER BY)
 * @param countParameters parameters of {@code countSql} (the data query may have more, e.g. in LIST subqueries)
 * @param columnUsages    columns used for joins, filters and sorting; input for index advice
 */
public record ReportQuery(String sql, List<Object> parameters, List<ResultColumn> columns, String countSql,
                          List<Object> countParameters, List<ColumnUsage> columnUsages) {
}
