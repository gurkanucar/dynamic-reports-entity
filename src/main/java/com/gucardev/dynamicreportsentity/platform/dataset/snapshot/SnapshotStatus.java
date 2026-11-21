package com.gucardev.dynamicreportsentity.platform.dataset.snapshot;

/** Refresh state of a snapshot. A snapshot stays readable while it is refreshed or after a failed refresh. */
public enum SnapshotStatus {
    /** Snapshot not built yet. */
    NEW,
    REFRESHING,
    /** LIVE snapshots are always READY. */
    READY,
    /** The last refresh failed ({@code lastError}); the previous snapshot, if any, is still served. */
    FAILED
}
