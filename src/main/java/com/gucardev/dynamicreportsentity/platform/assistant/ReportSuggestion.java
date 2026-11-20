package com.gucardev.dynamicreportsentity.platform.assistant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One request to the assistant and its outcome. Lives in the database so whichever instance a client polls can
 * answer; state changes are conditional updates ({@link ReportSuggestionRepository}), so a late worker and a timeout
 * never overwrite each other.
 */
@Entity
@Table(name = "report_suggestion", indexes = {
        @Index(name = "ix_report_suggestion_owner", columnList = "owner, created_at"),
        @Index(name = "ix_report_suggestion_created", columnList = "created_at")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportSuggestion {

    public enum Status { QUEUED, PROCESSING, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    /** The conversation and current definition as sent by the client. */
    @Column(name = "request_json", nullable = false, length = 200_000)
    private String requestJson;

    /** {@link ReportAssistant.Result} as JSON once completed. */
    @Column(name = "result_json", length = 200_000)
    private String resultJson;

    @Column(length = 60)
    private String errorCode;

    @Column(length = 1000)
    private String errorMessage;

    @Column(length = 200)
    private String model;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private Instant startedAt;
    private Instant finishedAt;

    ReportSuggestion(String owner, String requestJson, Instant now) {
        this.owner = owner;
        this.requestJson = requestJson;
        this.status = Status.QUEUED;
        this.createdAt = now;
    }

    public boolean isActive() {
        return status == Status.QUEUED || status == Status.PROCESSING;
    }
}
