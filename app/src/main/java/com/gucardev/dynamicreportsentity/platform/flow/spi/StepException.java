package com.gucardev.dynamicreportsentity.platform.flow.spi;

/**
 * A step failed for a known reason. {@code code} is shown on the run (e.g. {@code TEMPLATE_NOT_FOUND});
 * {@code retryable} says whether another attempt can help.
 */
public class StepException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public StepException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
