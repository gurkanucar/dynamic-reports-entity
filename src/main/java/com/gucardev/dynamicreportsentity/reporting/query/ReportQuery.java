package com.gucardev.dynamicreportsentity.reporting.query;

import java.util.List;

/**
 * Generated SQL. Identifiers come only from the catalog and generated aliases; every user value
 * is a {@code ?} parameter.
 *
 * @param columns output columns, in the same order as the SELECT list
 */
public record ReportQuery(String sql, List<Object> parameters, List<ResultColumn> columns) {
}
