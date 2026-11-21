package com.gucardev.dynamicreportsentity.platform.flow.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A file produced by a step (a dataset CSV). The content lives in the {@code FileStore} under {@link #storageKey};
 * this row says whose it is and until when it is kept. After {@link #expiresAt} the cleanup deletes the content and
 * sets {@link #deletedAt}; the row stays so the run can say the file has expired (410) instead of "not found".
 */
@Entity
@Table(name = "flow_artifact", indexes = {
        @Index(name = "ix_flow_artifact_run", columnList = "run_id"),
        @Index(name = "ix_flow_artifact_step", columnList = "step_run_id"),
        @Index(name = "ix_flow_artifact_expiry", columnList = "deleted_at, expires_at")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "step_run_id", nullable = false)
    private Long stepRunId;

    @Column(name = "file_name", nullable = false, length = 120)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 300)
    private String storageKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    RunArtifact(Long runId, Long stepRunId, String fileName, String contentType, long sizeBytes, String storageKey,
                Instant now, Instant expiresAt) {
        this.runId = runId;
        this.stepRunId = stepRunId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isAvailable(Instant now) {
        return deletedAt == null && now.isBefore(expiresAt);
    }

    void markDeleted(Instant now) {
        this.deletedAt = now;
    }
}
