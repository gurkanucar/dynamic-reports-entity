package com.gucardev.dynamicreportsentity.platform.dataset.snapshot;

import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetDefinition;

import java.time.Instant;

/**
 * A snapshot's definition and refresh state.
 *
 * @param rowCount      SNAPSHOT: rows in the current snapshot
 * @param refreshedAt   SNAPSHOT: when the served data was computed
 * @param refreshMillis SNAPSHOT: how long the last successful refresh took
 * @param nextRefreshAt SNAPSHOT: next automatic refresh (right after a create or change: now)
 * @param refreshingOn  the app instance refreshing it right now, if any
 * @param lastError     why the last refresh failed (the previous snapshot is still served)
 */
public record SnapshotInfo(String name, String label, String description, SnapshotMode mode, Integer refreshEveryHours,
                          SnapshotStatus status, Long rowCount, Instant refreshedAt, Long refreshMillis,
                          Instant nextRefreshAt, String refreshingOn, String lastError, DatasetDefinition definition,
                          String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
}
