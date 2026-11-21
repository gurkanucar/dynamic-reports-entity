package com.gucardev.dynamicreportsentity.platform.approval;

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
 * A decision someone waits for. Identified by its source ({@code sourceType} + {@code sourceId}), so the module
 * asking for it needs no table of its own. Changes state only under a row lock: a decision, a timeout and a cancel
 * can never both win.
 */
@Entity
@Table(name = "approval_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_approval_request_source",
                columnNames = {"source_type", "source_id"}),
        indexes = {
                @Index(name = "ix_approval_request_group", columnList = "group_id, status, created_at"),
                @Index(name = "ix_approval_request_due", columnList = "status, due_at")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalRequest {

    public enum Status { PENDING, APPROVED, REJECTED, TIMED_OUT, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;

    @Column(name = "source_id", nullable = false, length = 100)
    private String sourceId;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(length = 300)
    private String link;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(nullable = false, length = 100)
    private String groupName;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 4000)
    private String description;

    /** Who asked ({@code schedule:7}, {@code webhook:3} for automatic flow runs). */
    @Column(nullable = false, length = 100)
    private String requestedBy;

    /** Who the work runs for (e.g. the flow owner). */
    @Column(nullable = false, length = 100)
    private String onBehalfOf;

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

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    ApprovalRequest(NewApproval request, ApprovalGroup group, Instant now) {
        this.sourceType = request.sourceType();
        this.sourceId = request.sourceId();
        this.subject = cut(request.subject(), 200);
        this.link = request.link();
        this.groupId = group.getId();
        this.groupName = group.getName();
        this.title = cut(request.title(), 300);
        this.description = request.description() == null ? null : cut(request.description(), 4000);
        this.requestedBy = request.requestedBy();
        this.onBehalfOf = request.onBehalfOf();
        this.allowSelfApproval = request.allowSelfApproval();
        this.status = Status.PENDING;
        this.createdAt = now;
        this.dueAt = request.dueAt();
    }

    void decide(boolean approved, String user, String comment, Instant now) {
        finish(approved ? Status.APPROVED : Status.REJECTED, user, comment, now);
    }

    void timedOut(Instant now) {
        finish(Status.TIMED_OUT, null, null, now);
    }

    void cancelled(Instant now) {
        finish(Status.CANCELLED, null, null, now);
    }

    /** Whether {@code user} asked for it or it runs for them (self-approval). */
    public boolean isRequestedBy(String user) {
        return user.equals(requestedBy) || user.equals(onBehalfOf);
    }

    private void finish(Status finalStatus, String user, String comment, Instant now) {
        this.status = finalStatus;
        this.decidedBy = user;
        this.decidedAt = now;
        this.comment = comment == null ? null : cut(comment, 1000);
    }

    private static String cut(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }
}
