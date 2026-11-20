package com.gucardev.dynamicreportsentity.reporting.dataset;

/** A dataset request that cannot be served; {@link #kind} tells the web layer which HTTP status to use. */
public class DatasetException extends RuntimeException {

    public enum Kind {
        /** 404 */
        NOT_FOUND,
        /** 409: name taken, or the snapshot was not built yet */
        CONFLICT
    }

    private final Kind kind;

    DatasetException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
