package com.gucardev.dynamicreportsentity.platform.dataset.snapshot;

import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetResult;

import java.time.Instant;

/**
 * One page of a snapshot.
 *
 * @param refreshedAt SNAPSHOT: when the data was computed; LIVE: null (computed now)
 * @param result      same shape as {@code /api/datasets/run}; for a SNAPSHOT {@code total} is always filled
 *                    (known from the refresh, no extra query)
 */
public record SnapshotRows(String snapshot, SnapshotMode mode, Instant refreshedAt, DatasetResult result) {
}
