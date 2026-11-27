package com.gucardev.dynamicreportsentity.platform.flow.core;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

interface TriggerEventRepository extends JpaRepository<TriggerEvent, UUID> {

    boolean existsByTriggerIdAndEventKey(UUID triggerId, String eventKey);

    @Transactional
    @Modifying
    @Query("delete from TriggerEvent e where e.receivedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
