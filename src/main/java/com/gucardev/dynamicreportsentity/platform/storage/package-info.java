/**
 * File storage for the platform: {@link com.gucardev.dynamicreportsentity.platform.storage.FileStore}, backed by any
 * S3-compatible object store ({@code storage.type=s3}, the default) or a directory ({@code storage.type=filesystem},
 * for tests and single-node setups; several instances need a shared volume).
 * <p>
 * Stores bytes under keys and nothing else: who owns a file and how long it is kept is decided by the module that
 * writes it (e.g. flows delete their run files after {@code flows.file-retention}).
 */
@ApplicationModule(id = "storage", displayName = "File storage")
package com.gucardev.dynamicreportsentity.platform.storage;

import org.springframework.modulith.ApplicationModule;
