package com.gucardev.dynamicreportsentity.reporting.dataset;

import com.gucardev.dynamicreportsentity.reporting.query.ReportResult;

import java.time.Instant;

/**
 * One page of a dataset.
 *
 * @param refreshedAt SNAPSHOT: when the data was computed; LIVE: null (computed now)
 * @param result      same shape as {@code /api/reports/run}; for a SNAPSHOT {@code total} is always filled
 *                    (known from the refresh, no extra query)
 */
public record DatasetRows(String dataset, DatasetMode mode, Instant refreshedAt, ReportResult result) {
}
