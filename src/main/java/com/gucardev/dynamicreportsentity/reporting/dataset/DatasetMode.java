package com.gucardev.dynamicreportsentity.reporting.dataset;

/** How a dataset is served. */
public enum DatasetMode {
    /** Runs the saved report on every read, as the reader (always current, costs a query per read). */
    LIVE,
    /**
     * Served from a snapshot table that the engine refreshes every {@code refreshEveryHours} hours or on
     * demand (reads take milliseconds; data is as old as the last refresh).
     */
    SNAPSHOT
}
