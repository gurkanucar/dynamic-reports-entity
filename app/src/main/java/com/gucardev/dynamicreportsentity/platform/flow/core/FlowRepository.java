package com.gucardev.dynamicreportsentity.platform.flow.core;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlowRepository extends JpaRepository<Flow, UUID> {

    Page<Flow> findByOwner(String owner, Pageable pageable);

    /** Flows of one owner ({@code owner} null = all) whose name or description matches {@code q} (a LIKE pattern). */
    @Query("""
            select f from Flow f
             where (:owner is null or f.owner = :owner)
               and (:q is null or lower(f.name) like :q escape '\\' or lower(f.description) like :q escape '\\')""")
    Page<Flow> search(@Param("owner") String owner, @Param("q") String q, Pageable pageable);

    @Query("select count(f) > 0 from Flow f join f.references r where r.kind = :kind and r.refId = :refId")
    boolean existsReference(@Param("kind") String kind, @Param("refId") String refId);
}
