package com.gucardev.dynamicreportsentity.personnel.internal;

import com.gucardev.dynamicreportsentity.personnel.Personnel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PersonnelRepository extends JpaRepository<Personnel, Long> {

    Optional<Personnel> findByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    boolean existsByUsername(String username);

    /** The admin list: {@code q} is a LIKE pattern over name, e-mail and username; empty lists filter nothing. */
    @Query("""
            select p from Personnel p
             where (:q is null or lower(p.fullName) like :q escape '\\' or lower(p.email) like :q escape '\\'
                    or lower(p.username) like :q escape '\\')
               and (:anyDepartment = true or p.departmentId in :departments)
               and (:active is null or p.active = :active)
               and (:managerId is null or p.managerId = :managerId)
               and (:hiredFrom is null or p.hireDate >= :hiredFrom)
               and (:hiredTo is null or p.hireDate <= :hiredTo)""")
    Page<Personnel> search(@Param("q") String q, @Param("anyDepartment") boolean anyDepartment,
                           @Param("departments") Collection<Long> departments, @Param("active") Boolean active,
                           @Param("managerId") Long managerId, @Param("hiredFrom") LocalDate hiredFrom,
                           @Param("hiredTo") LocalDate hiredTo, Pageable pageable);

    @Query("select p.departmentId, count(p) from Personnel p where p.departmentId in :ids group by p.departmentId")
    List<Object[]> countByDepartment(@Param("ids") Collection<Long> ids);

    @Query("select p.managerId, count(p) from Personnel p where p.managerId in :ids group by p.managerId")
    List<Object[]> countReports(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query("update Personnel p set p.managerId = null where p.managerId = :id")
    int clearManager(@Param("id") long id);
}
