package com.gucardev.dynamicreportsentity.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

interface ProjectAssignmentRepository extends JpaRepository<ProjectAssignment, Long> {

    Page<ProjectAssignment> findByProjectId(Long projectId, Pageable pageable);

    boolean existsByProjectIdAndPersonnelId(Long projectId, Long personnelId);

    boolean existsByProjectIdAndPersonnelIdAndIdNot(Long projectId, Long personnelId, Long id);

    /** {projectId, assignments, total allocation} per project. */
    @Query("""
            select a.projectId, count(a), coalesce(sum(a.allocationPercent), 0) from ProjectAssignment a
             where a.projectId in :ids group by a.projectId""")
    List<Object[]> summarize(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query("delete from ProjectAssignment a where a.projectId = :id")
    int deleteByProject(@Param("id") long id);

    @Modifying
    @Query("delete from ProjectAssignment a where a.personnelId = :id")
    int deleteByPersonnel(@Param("id") long id);
}
