package com.gucardev.dynamicreportsentity.platform.dataset.snapshot;

/** A snapshot request that cannot be served; {@link #kind} tells the web layer which HTTP status to use. */
public class SnapshotException extends RuntimeException {

    public enum Kind {
        /** 404 */
        NOT_FOUND,
        /** 409: name taken, in use, or the snapshot was not built yet */
        CONFLICT,
        /** 403: not the owner, or SNAPSHOT mode without being an admin */
        FORBIDDEN
    }

    private final Kind kind;

    SnapshotException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
