package com.gucardev.dynamicreportsentity.platform.approval;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

interface ApprovalGroupRepository extends JpaRepository<ApprovalGroup, UUID> {

    List<ApprovalGroup> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    @Query("select g.id from ApprovalGroup g join g.members m where m.username = :username")
    Set<UUID> findIdsByMember(@Param("username") String username);
}
