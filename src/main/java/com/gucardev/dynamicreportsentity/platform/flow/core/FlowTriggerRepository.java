package com.gucardev.dynamicreportsentity.platform.flow.core;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

interface FlowTriggerRepository extends JpaRepository<FlowTrigger, Long> {

    List<FlowTrigger> findByFlowIdOrderById(Long flowId);

    long countByFlowId(Long flowId);

    Optional<FlowTrigger> findByWebhookToken(String token);

    List<FlowTrigger> findByTypeAndEnabledTrueAndTopic(FlowTrigger.Type type, String topic);

    @Query("select distinct t.topic from FlowTrigger t where t.type = :type and t.enabled = true")
    Set<String> findEnabledTopics(@Param("type") FlowTrigger.Type type);

    /** Bookkeeping without touching the version (an editor's ETag stays valid when events arrive). */
    @Modifying
    @Query("update FlowTrigger t set t.lastFiredAt = :at, t.lastRunId = :runId where t.id = :id")
    void markFired(@Param("id") Long id, @Param("at") Instant at, @Param("runId") Long runId);

    @Modifying
    @Query("delete from FlowTrigger t where t.flowId = :flowId")
    void deleteByFlowId(@Param("flowId") Long flowId);
}
