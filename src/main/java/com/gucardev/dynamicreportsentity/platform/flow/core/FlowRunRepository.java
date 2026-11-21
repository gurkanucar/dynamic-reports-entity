package com.gucardev.dynamicreportsentity.platform.flow.core;

import java.util.UUID;
import java.time.Instant;

import com.gucardev.dynamicreportsentity.platform.flow.spi.TriggerType;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

interface FlowRunRepository extends JpaRepository<FlowRun, UUID> {

    /** Every change of a run's state goes through this lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FlowRun r where r.id = :id")
    Optional<FlowRun> lock(@Param("id") UUID id);

    boolean existsByFlowIdAndStatusIn(UUID flowId, Collection<RunStatus> statuses);

    @Query("select r.id from FlowRun r where r.id in :ids and r.cancelRequested = true")
    Set<UUID> findCancelRequested(@Param("ids") Collection<UUID> ids);

    /**
     * Runs visible to a user ({@code runAs} null = all), optionally of one flow and in some statuses.
     */
    @Query("""
            select r from FlowRun r
             where (:runAs is null or r.runAs = :runAs)
               and (:flowId is null or r.flowId = :flowId)
               and r.status in :statuses
               and r.triggerType in :triggers
               and (:q is null or lower(r.flowName) like :q escape '\\')
               and (:from is null or r.createdAt >= :from)
               and (:to is null or r.createdAt < :to)""")
    Page<FlowRun> search(@Param("runAs") String runAs, @Param("flowId") UUID flowId,
                         @Param("statuses") Collection<RunStatus> statuses,
                         @Param("triggers") Collection<TriggerType> triggers, @Param("q") String q,
                         @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    List<FlowRun> findByFlowIdAndStatusIn(UUID flowId, Collection<RunStatus> statuses);
}
