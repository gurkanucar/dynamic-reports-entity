package com.gucardev.dynamicreportsentity.platform.dataset;

/** The viewer is not allowed to use a field, or to see any rows. Mapped to HTTP 403. */
public class DatasetAccessDeniedException extends RuntimeException {

    public DatasetAccessDeniedException(String message) {
        super(message);
    }
}
