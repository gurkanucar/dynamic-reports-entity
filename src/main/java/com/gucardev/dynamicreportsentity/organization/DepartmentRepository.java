package com.gucardev.dynamicreportsentity.organization;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

interface DepartmentRepository extends JpaRepository<Department, Long> {

    /** {@code q}: a LIKE pattern over name and cost center; {@code parentId}: only its children; {@code roots}: only top level. */
    @Query("""
            select d from Department d
             where (:q is null or lower(d.name) like :q escape '\\' or lower(d.costCenter) like :q escape '\\')
               and (:parentId is null or d.parentId = :parentId)
               and (:roots = false or d.parentId is null)""")
    Page<Department> search(@Param("q") String q, @Param("parentId") Long parentId, @Param("roots") boolean roots,
                            Pageable pageable);

    List<Department> findAllByOrderByName();

    @Query("select d.parentId, count(d) from Department d where d.parentId in :ids group by d.parentId")
    List<Object[]> countChildren(@Param("ids") Collection<Long> ids);

    long countByParentId(Long parentId);
}
