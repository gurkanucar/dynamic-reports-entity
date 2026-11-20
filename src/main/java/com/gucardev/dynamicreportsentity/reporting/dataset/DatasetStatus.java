package com.gucardev.dynamicreportsentity.reporting.dataset;

/** Refresh state of a dataset. A snapshot stays readable while it is refreshed or after a failed refresh. */
public enum DatasetStatus {
    /** Snapshot not built yet. */
    NEW,
    REFRESHING,
    /** LIVE datasets are always READY. */
    READY,
    /** The last refresh failed ({@code lastError}); the previous snapshot, if any, is still served. */
    FAILED
}
