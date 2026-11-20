package com.gucardev.dynamicreportsentity.reporting.query;

import java.util.List;

/**
 * One page of tabular report output.
 *
 * @param count          rows on this page
 * @param hasNext        whether another page follows (free: one extra row is fetched)
 * @param total          rows in the whole report, only with {@code includeTotal=true} (off by default, else
 *                       {@code null}); a separate {@code COUNT(*)} query runs only when there is a next page
 *                       (otherwise it is derived from the page).
 * @param truncatedLists labels of LIST columns where at least one cell hit its limit and was cut
 * @param indexHints     columns this report used that no index serves, with suggested DDL (empty if all good)
 */
public record ReportResult(List<ResultColumn> columns, List<List<Object>> rows, int count,
                           int page, int size, boolean hasNext, Long total,
                           List<String> truncatedLists, List<String> indexHints) {
}
