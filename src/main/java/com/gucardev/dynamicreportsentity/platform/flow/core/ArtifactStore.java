package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.storage.FileStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Files of flow runs: metadata in {@code flow_artifact}, content in the {@link FileStore} (S3). Kept for
 * {@code flows.file-retention}, then deleted by {@link #deleteExpired}.
 * <p>
 * The row is written before the upload, so a file whose upload or run fails half way is still found and deleted
 * by the cleanup: no orphaned objects in the bucket.
 */
@Slf4j
@Component
public class ArtifactStore {

    private final RunArtifactRepository artifacts;
    private final FileStore files;
    private final FlowProperties properties;
    private final Clock clock;

    ArtifactStore(RunArtifactRepository artifacts, FileStore files, FlowProperties properties, Clock clock) {
        this.artifacts = artifacts;
        this.files = files;
        this.properties = properties;
        this.clock = clock;
    }

    RunArtifact save(long runId, long stepRunId, String fileName, String contentType, Path file) {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Instant now = clock.instant();
        String key = "flows/runs/" + runId + "/" + stepRunId + "/" + UUID.randomUUID() + "/" + fileName;
        RunArtifact artifact = artifacts.save(new RunArtifact(runId, stepRunId, fileName, contentType, size, key, now,
                now.plus(properties.fileRetention())));
        try {
            files.put(key, file, contentType);
        } catch (RuntimeException e) {
            artifacts.delete(artifact);
            throw e;
        }
        return artifact;
    }

    /** Files of earlier attempts of a step (each attempt writes its own). */
    void deleteForStep(long stepRunId) {
        for (RunArtifact artifact : artifacts.findByStepRunIdAndDeletedAtIsNull(stepRunId)) {
            files.delete(artifact.getStorageKey());
            artifacts.delete(artifact);
        }
    }

    public Optional<RunArtifact> find(long id) {
        return artifacts.findById(id);
    }

    public List<RunArtifact> forRun(long runId) {
        return artifacts.findByRunIdOrderById(runId);
    }

    public InputStream open(RunArtifact artifact) {
        return files.open(artifact.getStorageKey());
    }

    /** Presigned URL for a direct download from the store; empty = stream {@link #open} through the app. */
    public Optional<URI> downloadUrl(RunArtifact artifact) {
        return files.temporaryDownloadUrl(artifact.getStorageKey(), artifact.getFileName(), artifact.getContentType());
    }

    byte[] load(long id) {
        RunArtifact artifact = artifacts.findById(id).orElse(null);
        if (artifact == null || !artifact.isAvailable(clock.instant())) {
            return null;
        }
        try (InputStream in = files.open(artifact.getStorageKey())) {
            return in.readAllBytes();
        } catch (FileStore.StoredFileMissingException e) {
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Deletes up to {@code limit} expired files. Every instance may run this at the same time: each locks a different
     * batch ({@code SKIP LOCKED}). A file whose deletion fails stays for the next round.
     *
     * @return how many expired rows were looked at (less than {@code limit}: nothing more to do now)
     */
    @Transactional
    public int deleteExpired(int limit) {
        Instant now = clock.instant();
        List<RunArtifact> expired = artifacts.lockExpired(now, limit);
        for (RunArtifact artifact : expired) {
            try {
                files.delete(artifact.getStorageKey());
                artifact.markDeleted(now);
            } catch (RuntimeException e) {
                log.warn("Could not delete expired file {} ({}); will try again: {}", artifact.getId(),
                        artifact.getStorageKey(), e.getMessage());
            }
        }
        return expired.size();
    }
}
