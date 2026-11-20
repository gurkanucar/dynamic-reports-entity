package com.gucardev.dynamicreportsentity.reporting.query;

import java.util.List;

/**
 * One page of tabular report output. No total count on purpose: {@code COUNT(*)} over a big
 * join is often more expensive than the page itself; {@code hasNext} is enough for paging.
 */
public record ReportResult(List<ResultColumn> columns, List<List<Object>> rows,
                           int page, int size, boolean hasNext) {
}
