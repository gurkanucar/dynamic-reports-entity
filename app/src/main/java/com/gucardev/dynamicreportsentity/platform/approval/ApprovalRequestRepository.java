package com.gucardev.dynamicreportsentity.platform.approval;

import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    Optional<ApprovalRequest> findBySourceTypeAndSourceId(String sourceType, String sourceId);

    /** The request, locked until the transaction ends (decisions, timeouts and cancels wait for each other). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ApprovalRequest r where r.id = :id")
    Optional<ApprovalRequest> lock(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ApprovalRequest r where r.sourceType = :type and r.sourceId = :id")
    Optional<ApprovalRequest> lockBySource(@Param("type") String sourceType, @Param("id") String sourceId);

    /** Pending requests past their deadline; rows another instance holds are skipped, not waited for. */
    @Query(value = """
            SELECT CAST(id AS VARCHAR(36)) FROM approval_request
             WHERE status = 'PENDING' AND due_at <= :now
             ORDER BY due_at
             LIMIT :limit
             FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<UUID> lockOverdue(@Param("now") Instant now, @Param("limit") int limit);

    /** Requests of the given groups (or all), filtered by status, group and title text ({@code q}: a LIKE pattern). */
    @Query("""
            select r from ApprovalRequest r
             where (:all = true or r.groupId in :groupIds)
               and r.status in :statuses
               and (:anyGroup = true or r.groupId in :onlyGroups)
               and (:q is null or lower(r.title) like :q escape '\\' or lower(r.groupName) like :q escape '\\')""")
    Page<ApprovalRequest> search(@Param("all") boolean all, @Param("groupIds") Collection<UUID> groupIds,
                                 @Param("statuses") Collection<ApprovalRequest.Status> statuses,
                                 @Param("anyGroup") boolean anyGroup, @Param("onlyGroups") Collection<UUID> onlyGroups,
                                 @Param("q") String q, Pageable pageable);

    boolean existsByGroupIdAndStatus(UUID groupId, ApprovalRequest.Status status);
}
