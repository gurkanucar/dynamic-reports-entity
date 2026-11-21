package com.gucardev.dynamicreportsentity.platform.dataset.snapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A saved dataset definition with a name, served LIVE or from a refreshed SNAPSHOT table.
 * <p>
 * Shared by every app instance through the database:
 * <ul>
 *   <li>A refresh runs on one instance only: it {@link SnapshotRepository#claim claims} the snapshot
 *       ({@link #refreshOwner} + {@link #refreshLeaseUntil}, renewed while it runs). A crashed instance's
 *       lease simply expires and another instance takes over.</li>
 *   <li>{@link #version} (optimistic locking) keeps an edit and a finishing refresh from overwriting
 *       each other.</li>
 *   <li>The snapshot table ({@code rds_<name>_<snapshotVersion>}) is built under a version number taken
 *       in the claim, so two refreshes never write the same table; readers switch with {@link #tableName}.</li>
 * </ul>
 */
@Entity
@Table(name = "dataset_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Snapshot {

    @Id
    @Column(length = 40)
    private String name;

    /** Null until first saved (tells Spring Data the snapshot is new). */
    @Version
    private Long version;

    private String label;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_mode", nullable = false, length = 20)
    private SnapshotMode mode;

    /** SNAPSHOT: refresh period in hours; null = refreshed on demand only. */
    private Integer refreshEveryHours;

    /** The {@code DatasetDefinition} as JSON. */
    @Column(name = "definition_json", nullable = false, length = 100_000)
    private String definitionJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SnapshotStatus status;

    /** Current snapshot table, null until the first successful refresh. */
    @Column(length = 80)
    private String tableName;

    /** Output columns of the current snapshot ({@code ResultColumn} list as JSON). */
    @Column(name = "columns_json", length = 100_000)
    private String columnsJson;

    /** Taken (incremented) by every claim; part of the snapshot table name. */
    private long snapshotVersion;

    private Long rowCount;
    private Instant refreshedAt;
    private Long refreshMillis;
    /** Next automatic refresh; also "now" right after a create or change, so some instance builds it. */
    private Instant nextRefreshAt;

    /** Instance running the refresh right now (with {@link #refreshLeaseUntil} in the future). */
    @Column(length = 200)
    private String refreshOwner;
    private Instant refreshLeaseUntil;

    @Column(length = 2000)
    private String lastError;

    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;

    Snapshot(String name, String createdBy, Instant now) {
        this.name = name;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.status = SnapshotStatus.NEW;
    }

    void define(String label, String description, SnapshotMode mode, Integer refreshEveryHours, String definitionJson,
                String user, Instant now) {
        this.label = label;
        this.description = description;
        this.mode = mode;
        this.refreshEveryHours = refreshEveryHours;
        this.definitionJson = definitionJson;
        this.updatedBy = user;
        this.updatedAt = now;
        if (mode == SnapshotMode.LIVE) {
            status = SnapshotStatus.READY;
            nextRefreshAt = null;
        } else {
            if (status == SnapshotStatus.READY && tableName == null) {
                status = SnapshotStatus.NEW;      // was LIVE until now
            }
            nextRefreshAt = now;                 // (re)build with the new definition on the next occasion
        }
    }

    boolean isRefreshedBy(String instance, Instant now) {
        return instance.equals(refreshOwner) && refreshLeaseUntil != null && refreshLeaseUntil.isAfter(now);
    }

    boolean isBeingRefreshed(Instant now) {
        return refreshOwner != null && refreshLeaseUntil != null && refreshLeaseUntil.isAfter(now);
    }

    /** @return the previous snapshot table, to be dropped */
    String refreshed(String table, String columnsJson, long rowCount, long millis, Instant now) {
        String previous = tableName;
        this.tableName = table;
        this.columnsJson = columnsJson;
        this.rowCount = rowCount;
        this.refreshMillis = millis;
        this.refreshedAt = now;
        this.lastError = null;
        this.status = SnapshotStatus.READY;
        releaseLease(now);
        return previous;
    }

    void refreshFailed(String error, Instant now) {
        this.lastError = error.length() > 2000 ? error.substring(0, 2000) : error;
        this.status = SnapshotStatus.FAILED;
        releaseLease(now);
    }

    /** The refresh was abandoned (snapshot switched to LIVE meanwhile): just give the lease back. */
    void refreshAbandoned(Instant now) {
        if (status == SnapshotStatus.REFRESHING) {
            status = mode == SnapshotMode.LIVE ? SnapshotStatus.READY
                    : tableName == null ? SnapshotStatus.NEW : SnapshotStatus.READY;
        }
        refreshOwner = null;
        refreshLeaseUntil = null;
    }

    /** @return the snapshot table that is no longer needed (when switching to LIVE) */
    String dropSnapshot() {
        String previous = tableName;
        tableName = null;
        columnsJson = null;
        rowCount = null;
        refreshedAt = null;
        refreshMillis = null;
        return previous;
    }

    private void releaseLease(Instant now) {
        refreshOwner = null;
        refreshLeaseUntil = null;
        nextRefreshAt = refreshEveryHours == null ? null : now.plusSeconds(refreshEveryHours * 3600L);
    }
}
