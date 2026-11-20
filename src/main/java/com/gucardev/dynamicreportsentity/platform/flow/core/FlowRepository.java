package com.gucardev.dynamicreportsentity.platform.flow.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlowRepository extends JpaRepository<Flow, Long> {

    Page<Flow> findByOwner(String owner, Pageable pageable);

    boolean existsByTemplateIdsContaining(Long templateId);
}
