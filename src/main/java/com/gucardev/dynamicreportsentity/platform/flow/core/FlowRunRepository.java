package com.gucardev.dynamicreportsentity.platform.flow.core;

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

interface FlowRunRepository extends JpaRepository<FlowRun, Long> {

    /** Every change of a run's state goes through this lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FlowRun r where r.id = :id")
    Optional<FlowRun> lock(@Param("id") Long id);

    boolean existsByFlowIdAndStatusIn(Long flowId, Collection<RunStatus> statuses);

    @Query("select r.id from FlowRun r where r.id in :ids and r.cancelRequested = true")
    Set<Long> findCancelRequested(@Param("ids") Collection<Long> ids);

    /**
     * Runs visible to a user ({@code runAs} null = all), optionally of one flow and in some statuses.
     */
    @Query("""
            select r from FlowRun r
             where (:runAs is null or r.runAs = :runAs)
               and (:flowId is null or r.flowId = :flowId)
               and r.status in :statuses""")
    Page<FlowRun> search(@Param("runAs") String runAs, @Param("flowId") Long flowId,
                         @Param("statuses") Collection<RunStatus> statuses, Pageable pageable);

    List<FlowRun> findByFlowIdAndStatusIn(Long flowId, Collection<RunStatus> statuses);
}
