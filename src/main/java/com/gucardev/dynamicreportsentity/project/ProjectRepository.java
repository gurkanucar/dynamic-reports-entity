package com.gucardev.dynamicreportsentity.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("""
            select p from Project p
             where (:q is null or lower(p.code) like :q escape '\\' or lower(p.name) like :q escape '\\')
               and (:anyStatus = true or p.status in :statuses)
               and (:departmentId is null or p.departmentId = :departmentId)
               and (:budgetMin is null or p.budget >= :budgetMin)
               and (:budgetMax is null or p.budget <= :budgetMax)""")
    Page<Project> search(@Param("q") String q, @Param("anyStatus") boolean anyStatus,
                         @Param("statuses") Collection<String> statuses, @Param("departmentId") Long departmentId,
                         @Param("budgetMin") BigDecimal budgetMin, @Param("budgetMax") BigDecimal budgetMax,
                         Pageable pageable);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    @Query("select p.departmentId, count(p) from Project p where p.departmentId in :ids group by p.departmentId")
    List<Object[]> countByDepartment(@Param("ids") Collection<Long> ids);
}
