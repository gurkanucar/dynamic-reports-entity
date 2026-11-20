package com.gucardev.dynamicreportsentity.reporting.dataset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

interface ReportDatasetRepository extends JpaRepository<ReportDataset, String> {

    List<ReportDataset> findAllByOrderByNameAsc();

    List<ReportDataset> findByModeAndNextRefreshAtLessThanEqual(DatasetMode mode, Instant now);

    /**
     * Atomically takes the refresh of a SNAPSHOT dataset for {@code owner}, unless another instance holds
     * an unexpired lease. Also takes the next snapshot version, so the new table name is unique, and marks
     * the dataset due: should this instance die, the schedulers of the others complete the refresh.
     *
     * @return 1 if claimed, 0 if someone else is refreshing it (or it is not a snapshot)
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ReportDataset d
               set d.status = com.gucardev.dynamicreportsentity.reporting.dataset.DatasetStatus.REFRESHING,
                   d.refreshOwner = :owner, d.refreshLeaseUntil = :until,
                   d.snapshotVersion = d.snapshotVersion + 1, d.version = d.version + 1, d.nextRefreshAt = :now
             where d.name = :name
               and d.mode = com.gucardev.dynamicreportsentity.reporting.dataset.DatasetMode.SNAPSHOT
               and (d.refreshLeaseUntil is null or d.refreshLeaseUntil < :now)""")
    int claim(@Param("name") String name, @Param("owner") String owner, @Param("now") Instant now,
              @Param("until") Instant until);

    /**
     * {@link #claim} for background refreshes: only while the dataset is still due. Several instances may
     * have picked the same due dataset; once one has refreshed it, it is no longer due and the others skip it.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ReportDataset d
               set d.status = com.gucardev.dynamicreportsentity.reporting.dataset.DatasetStatus.REFRESHING,
                   d.refreshOwner = :owner, d.refreshLeaseUntil = :until,
                   d.snapshotVersion = d.snapshotVersion + 1, d.version = d.version + 1
             where d.name = :name
               and d.mode = com.gucardev.dynamicreportsentity.reporting.dataset.DatasetMode.SNAPSHOT
               and d.nextRefreshAt <= :now
               and (d.refreshLeaseUntil is null or d.refreshLeaseUntil < :now)""")
    int claimDue(@Param("name") String name, @Param("owner") String owner, @Param("now") Instant now,
                 @Param("until") Instant until);

    /** Extends the lease while the refresh runs; 0 means the lease was lost (expired and taken over). */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ReportDataset d set d.refreshLeaseUntil = :until, d.version = d.version + 1
             where d.name = :name and d.refreshOwner = :owner""")
    int renew(@Param("name") String name, @Param("owner") String owner, @Param("until") Instant until);
}
