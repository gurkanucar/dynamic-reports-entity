package com.gucardev.dynamicreportsentity.platform.flow.core;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface RunEventRepository extends JpaRepository<RunEvent, Long> {

    List<RunEvent> findByRunIdOrderById(Long runId);
}
