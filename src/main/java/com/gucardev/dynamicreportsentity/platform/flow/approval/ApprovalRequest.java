package com.gucardev.dynamicreportsentity.platform.flow.approval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A decision an approval step waits for. One per step run; it changes state only while the run's row is locked
 * (together with the step), so a decision, a timeout and a cancel can never both win.
 */
@Entity
@Table(name = "approval_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_approval_request_step", columnNames = "step_run_id"),
        indexes = {
                @Index(name = "ix_approval_request_group", columnList = "group_id, status, created_at"),
                @Index(name = "ix_approval_request_run", columnList = "run_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalRequest {

    public enum Status { PENDING, APPROVED, REJECTED, TIMED_OUT, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "step_run_id", nullable = false)
    private Long stepRunId;

    @Column(nullable = false)
    private Long flowId;

    @Column(nullable = false, length = 100)
    private String flowName;

    @Column(nullable = false, length = 40)
    private String stepKey;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(nullable = false, length = 100)
    private String groupName;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 4000)
    private String description;

    /** Who started the run ({@code schedule:7}, {@code webhook:3} for automatic runs). */
    @Column(nullable = false, length = 100)
    private String requestedBy;

    /** The flow owner. */
    @Column(nullable = false, length = 100)
    private String runAs;

    private boolean allowSelfApproval;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(length = 100)
    private String decidedBy;

    private Instant decidedAt;

    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant dueAt;

    public ApprovalRequest(Long runId, Long stepRunId, Long flowId, String flowName, String stepKey, ApprovalGroup group,
                           String title, String description, String requestedBy, String runAs,
                           boolean allowSelfApproval, Instant now, Instant dueAt) {
        this.runId = runId;
        this.stepRunId = stepRunId;
        this.flowId = flowId;
        this.flowName = flowName;
        this.stepKey = stepKey;
        this.groupId = group.getId();
        this.groupName = group.getName();
        this.title = title.length() > 300 ? title.substring(0, 300) : title;
        this.description = description != null && description.length() > 4000 ? description.substring(0, 4000)
                : description;
        this.requestedBy = requestedBy;
        this.runAs = runAs;
        this.allowSelfApproval = allowSelfApproval;
        this.status = Status.PENDING;
        this.createdAt = now;
        this.dueAt = dueAt;
    }

    public void decide(boolean approved, String user, String comment, Instant now) {
        finish(approved ? Status.APPROVED : Status.REJECTED, user, comment, now);
    }

    public void timedOut(Instant now) {
        finish(Status.TIMED_OUT, null, null, now);
    }

    public void cancelled(Instant now) {
        finish(Status.CANCELLED, null, null, now);
    }

    /** Whether {@code user} started the run or owns the flow (self-approval). */
    public boolean isRequestedBy(String user) {
        return user.equals(requestedBy) || user.equals(runAs);
    }

    private void finish(Status finalStatus, String user, String comment, Instant now) {
        this.status = finalStatus;
        this.decidedBy = user;
        this.decidedAt = now;
        this.comment = comment != null && comment.length() > 1000 ? comment.substring(0, 1000) : comment;
    }
}
