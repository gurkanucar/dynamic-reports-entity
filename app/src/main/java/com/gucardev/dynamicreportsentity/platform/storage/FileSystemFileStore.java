package com.gucardev.dynamicreportsentity.platform.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * {@link FileStore} in a directory. Several instances need the directory on a shared volume (ReadWriteMany).
 * Files are written to a temporary name and moved into place, so readers never see half a file.
 */
class FileSystemFileStore implements FileStore {

    private final Path root;

    FileSystemFileStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void put(String key, Path file, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path partial = Files.createTempFile(target.getParent(), ".upload-", ".part");
            Files.copy(file, partial, StandardCopyOption.REPLACE_EXISTING);
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public InputStream open(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (NoSuchFileException e) {
            throw new StoredFileMissingException(key, e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<URI> temporaryDownloadUrl(String key, String fileName, String contentType) {
        return Optional.empty();
    }

    /** Keys are relative paths; nothing may point outside the root. */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("Invalid storage key: " + key);
        }
        return path;
    }
}
