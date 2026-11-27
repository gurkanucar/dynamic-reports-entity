package com.gucardev.dynamicreportsentity.platform.flow.core;

import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface FlowScheduleRepository extends JpaRepository<FlowSchedule, UUID> {

    List<FlowSchedule> findByFlowIdOrderById(UUID flowId);

    long countByFlowId(UUID flowId);

    /** Candidates only (no lock); {@link #lockIfDue} decides. */
    @Query("select s.id from FlowSchedule s where s.enabled = true and s.nextFireAt <= :now order by s.nextFireAt")
    List<UUID> findDueIds(@Param("now") Instant now, Limit limit);

    /**
     * Locks the schedule if it is still due; empty if another instance holds it (it is firing it right now) or
     * already moved it on. Never waits.
     */
    @Query(value = """
            SELECT CAST(id AS VARCHAR(36)) FROM flow_schedule
             WHERE id = :id AND enabled = TRUE AND next_fire_at <= :now
             FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    Optional<UUID> lockIfDue(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying
    @Query("delete from FlowSchedule s where s.flowId = :flowId")
    void deleteByFlowId(@Param("flowId") UUID flowId);
}
