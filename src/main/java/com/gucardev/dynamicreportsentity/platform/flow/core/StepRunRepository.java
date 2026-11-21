package com.gucardev.dynamicreportsentity.platform.flow.core;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface StepRunRepository extends JpaRepository<StepRun, UUID> {

    /**
     * The best READY steps of a lane, with their runs, locked for the calling transaction. Rows another instance
     * holds are skipped instead of waited for ({@code SKIP LOCKED}): instances claiming at the same time get
     * different steps and never block each other. Steps of cancelled or finished runs are left out.
     */
    @Query(value = """
            SELECT CAST(s.id AS VARCHAR(36)) FROM flow_step_run s JOIN flow_run r ON r.id = s.run_id
             WHERE s.lane = :lane AND s.status = 'READY' AND s.available_at <= :now
               AND r.cancel_requested = FALSE AND r.status IN ('QUEUED', 'RUNNING')
             ORDER BY s.priority DESC, s.available_at, s.id
             LIMIT :limit
             FOR UPDATE OF s, r SKIP LOCKED""", nativeQuery = true)
    List<UUID> lockReady(@Param("lane") String lane, @Param("now") Instant now, @Param("limit") int limit);

    /** RUNNING steps whose lease ran out (their instance died or lost the database), locked like {@link #lockReady}. */
    @Query(value = """
            SELECT CAST(s.id AS VARCHAR(36)) FROM flow_step_run s JOIN flow_run r ON r.id = s.run_id
             WHERE s.status = 'RUNNING' AND s.lease_until < :now
             ORDER BY s.lease_until
             LIMIT :limit
             FOR UPDATE OF s, r SKIP LOCKED""", nativeQuery = true)
    List<UUID> lockExpiredLeases(@Param("now") Instant now, @Param("limit") int limit);

    /** WAITING steps (approvals) whose deadline passed, locked like {@link #lockReady}. */
    @Query(value = """
            SELECT CAST(s.id AS VARCHAR(36)) FROM flow_step_run s JOIN flow_run r ON r.id = s.run_id
             WHERE s.status = 'WAITING' AND s.available_at <= :now
             ORDER BY s.available_at
             LIMIT :limit
             FOR UPDATE OF s, r SKIP LOCKED""", nativeQuery = true)
    List<UUID> lockDueWaiting(@Param("now") Instant now, @Param("limit") int limit);

    List<StepRun> findByRunIdOrderById(UUID runId);

    @Query("select s.runId from StepRun s where s.id = :id")
    Optional<UUID> findRunId(@Param("id") UUID id);

    List<StepRun> findByRunIdAndStatus(UUID runId, StepStatus status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update StepRun s set s.leaseUntil = :until
             where s.id = :id and s.owner = :token
               and s.status = com.gucardev.dynamicreportsentity.platform.flow.core.StepStatus.RUNNING""")
    int renew(@Param("id") UUID id, @Param("token") String token, @Param("until") Instant until);
}
