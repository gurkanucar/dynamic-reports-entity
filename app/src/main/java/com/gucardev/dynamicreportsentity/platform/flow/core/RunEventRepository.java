package com.gucardev.dynamicreportsentity.platform.flow.core;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface RunEventRepository extends JpaRepository<RunEvent, UUID> {

    List<RunEvent> findByRunIdOrderById(UUID runId);
}
