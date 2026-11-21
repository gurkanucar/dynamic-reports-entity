package com.gucardev.dynamicreportsentity.platform.flow.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlowRepository extends JpaRepository<Flow, Long> {

    Page<Flow> findByOwner(String owner, Pageable pageable);

    @Query("select count(f) > 0 from Flow f join f.references r where r.kind = :kind and r.refId = :refId")
    boolean existsReference(@Param("kind") String kind, @Param("refId") String refId);
}
