package com.gucardev.dynamicreportsentity.platform.flow.spi;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/**
 * What a {@link StepHandler} gets for one attempt.
 */
public interface StepContext {

    ClaimedStep step();

    /** The attempt must finish by then; it is stopped afterwards. */
    Instant deadline();

    /**
     * Runs {@code action} when the attempt is stopped (cancel, timeout, shutdown, lost lease), e.g. to cancel a
     * query; right away if it already was. May run more than once.
     */
    void onStop(Runnable action);

    /** Stores a file of this step for {@code flows.file-retention}; earlier attempts' files are replaced. */
    StoredFile storeFile(String fileName, String contentType, Path file) throws IOException;

    /** A file of the run's steps ({@code steps.<key>.artifactId}); null if it expired or is gone. */
    byte[] loadFile(long fileId);

    record StoredFile(long id, long sizeBytes, Instant expiresAt) {
    }
}
