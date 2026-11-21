package com.gucardev.dynamicreportsentity.platform.dataset.snapshot;

/**
 * Implemented by modules that use saved datasets by name (e.g. flows), so one in use cannot be deleted without this
 * module knowing who uses it.
 */
public interface SnapshotUsage {

    boolean usesSnapshot(String name);
}
