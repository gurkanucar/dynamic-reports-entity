package com.gucardev.dynamicreportsentity.platform.dataset.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

interface SnapshotRepository extends JpaRepository<Snapshot, String> {

    List<Snapshot> findAllByOrderByNameAsc();

    List<Snapshot> findByModeAndNextRefreshAtLessThanEqual(SnapshotMode mode, Instant now);

    /**
     * Atomically takes the refresh of a SNAPSHOT snapshot for {@code owner}, unless another instance holds
     * an unexpired lease. Also takes the next snapshot version, so the new table name is unique, and marks
     * the snapshot due: should this instance die, the schedulers of the others complete the refresh.
     *
     * @return 1 if claimed, 0 if someone else is refreshing it (or it is not a snapshot)
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Snapshot d
               set d.status = com.gucardev.dynamicreportsentity.platform.dataset.snapshot.SnapshotStatus.REFRESHING,
                   d.refreshOwner = :owner, d.refreshLeaseUntil = :until,
                   d.snapshotVersion = d.snapshotVersion + 1, d.version = d.version + 1, d.nextRefreshAt = :now
             where d.name = :name
               and d.mode = com.gucardev.dynamicreportsentity.platform.dataset.snapshot.SnapshotMode.SNAPSHOT
               and (d.refreshLeaseUntil is null or d.refreshLeaseUntil < :now)""")
    int claim(@Param("name") String name, @Param("owner") String owner, @Param("now") Instant now,
              @Param("until") Instant until);

    /**
     * {@link #claim} for background refreshes: only while the snapshot is still due. Several instances may
     * have picked the same due snapshot; once one has refreshed it, it is no longer due and the others skip it.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Snapshot d
               set d.status = com.gucardev.dynamicreportsentity.platform.dataset.snapshot.SnapshotStatus.REFRESHING,
                   d.refreshOwner = :owner, d.refreshLeaseUntil = :until,
                   d.snapshotVersion = d.snapshotVersion + 1, d.version = d.version + 1
             where d.name = :name
               and d.mode = com.gucardev.dynamicreportsentity.platform.dataset.snapshot.SnapshotMode.SNAPSHOT
               and d.nextRefreshAt <= :now
               and (d.refreshLeaseUntil is null or d.refreshLeaseUntil < :now)""")
    int claimDue(@Param("name") String name, @Param("owner") String owner, @Param("now") Instant now,
                 @Param("until") Instant until);

    /** Extends the lease while the refresh runs; 0 means the lease was lost (expired and taken over). */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Snapshot d set d.refreshLeaseUntil = :until, d.version = d.version + 1
             where d.name = :name and d.refreshOwner = :owner""")
    int renew(@Param("name") String name, @Param("owner") String owner, @Param("until") Instant until);
}
