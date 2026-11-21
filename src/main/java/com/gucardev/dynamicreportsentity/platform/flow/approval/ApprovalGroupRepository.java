package com.gucardev.dynamicreportsentity.platform.flow.approval;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface ApprovalGroupRepository extends JpaRepository<ApprovalGroup, Long> {

    List<ApprovalGroup> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("select g.id from ApprovalGroup g join g.members m where m.username = :username")
    Set<Long> findIdsByMember(@Param("username") String username);
}
