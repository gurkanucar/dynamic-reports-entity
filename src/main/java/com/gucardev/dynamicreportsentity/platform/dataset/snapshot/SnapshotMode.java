package com.gucardev.dynamicreportsentity.platform.dataset.snapshot;

/** How a snapshot is served. */
public enum SnapshotMode {
    /** Runs the saved dataset on every read, as the reader (always current, costs a query per read). */
    LIVE,
    /**
     * Served from a snapshot table that the engine refreshes every {@code refreshEveryHours} hours or on
     * demand (reads take milliseconds; data is as old as the last refresh).
     */
    SNAPSHOT
}
