package com.gucardev.dynamicreportsentity.platform.reporting;

/** The viewer is not allowed to use a field, or to see any rows. Mapped to HTTP 403. */
public class ReportAccessDeniedException extends RuntimeException {

    public ReportAccessDeniedException(String message) {
        super(message);
    }
}
