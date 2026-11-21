package com.gucardev.dynamicreportsentity.platform.flow.approval;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    Optional<ApprovalRequest> findByStepRunId(Long stepRunId);

    @Query("select r.runId from ApprovalRequest r where r.id = :id")
    Optional<Long> findRunId(@Param("id") Long id);

    List<ApprovalRequest> findByRunIdAndStatus(Long runId, ApprovalRequest.Status status);

    /** Requests of the given groups (null = every group), newest first. */
    @Query("""
            select r from ApprovalRequest r
             where (:all = true or r.groupId in :groupIds)
               and r.status in :statuses""")
    Page<ApprovalRequest> search(@Param("all") boolean all, @Param("groupIds") Collection<Long> groupIds,
                                 @Param("statuses") Collection<ApprovalRequest.Status> statuses, Pageable pageable);

    boolean existsByGroupIdAndStatus(Long groupId, ApprovalRequest.Status status);
}
