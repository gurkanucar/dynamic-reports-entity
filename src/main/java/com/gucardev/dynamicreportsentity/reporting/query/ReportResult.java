package com.gucardev.dynamicreportsentity.reporting.query;

import java.util.List;

/**
 * One page of tabular report output.
 * <p>
 * {@code hasNext} is always there and costs nothing (one extra row is fetched). {@code total} is
 * only computed on request ({@code includeTotal=true}), because it needs a second query that has to
 * evaluate every matching row/group, which on large joins can cost more than the page itself.
 *
 * @param total      number of rows of the whole report, or {@code null} if not requested
 * @param indexHints columns this report used that no index serves, with suggested DDL (empty if all good)
 */
public record ReportResult(List<ResultColumn> columns, List<List<Object>> rows,
                           int page, int size, boolean hasNext, Long total, List<String> indexHints) {
}
