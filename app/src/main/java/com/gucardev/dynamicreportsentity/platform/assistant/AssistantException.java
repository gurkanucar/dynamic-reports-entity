package com.gucardev.dynamicreportsentity.platform.assistant;

/**
 * A suggestion that cannot be made, or a request to the assistant API that cannot be served. {@code code} is stable
 * (see {@code api/errors.yaml}); {@code status} is the HTTP status when it is thrown by a request.
 */
public class AssistantException extends RuntimeException {

    private final int status;
    private final String code;

    public AssistantException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
