package com.gucardev.dynamicreportsentity.platform.storage;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Bytes under keys. Implementations are safe to use from several app instances at once (keys written once, never
 * changed; deleting is idempotent).
 */
public interface FileStore {

    /** Stores the file (streamed, never fully in memory). Overwrites an existing key. */
    void put(String key, Path file, String contentType);

    /**
     * Opens the content for reading; the caller closes the stream.
     *
     * @throws StoredFileMissingException if there is nothing under the key
     */
    InputStream open(String key);

    /** Removes the key; nothing happens if it does not exist. */
    void delete(String key);

    /**
     * A short-lived URL the browser can download the file from directly (S3 presigned URL), so the bytes do not
     * pass through the app. Empty when the store cannot offer one: stream {@link #open} instead.
     */
    Optional<URI> temporaryDownloadUrl(String key, String fileName, String contentType);

    /** There is no file under the requested key (deleted, or never written). */
    class StoredFileMissingException extends RuntimeException {
        public StoredFileMissingException(String key, Throwable cause) {
            super("No stored file under '" + key + "'", cause);
        }
    }
}
