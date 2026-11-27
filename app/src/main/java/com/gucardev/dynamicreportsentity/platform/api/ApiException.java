package com.gucardev.dynamicreportsentity.platform.api;

import java.util.List;

/**
 * A request that cannot be served, in any platform module. {@link Kind} decides the HTTP status, {@code code} is the
 * stable machine-readable error code, {@code errors} lists field problems. Modules subclass it for their own
 * factories (e.g. {@code FlowException}) but share the HTTP mapping ({@link ApiExceptionHandlerSupport}).
 */
public class ApiException extends RuntimeException {

    public enum Kind {
        BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, GONE, PRECONDITION_FAILED, UNPROCESSABLE,
        PRECONDITION_REQUIRED
    }

    /** A problem with one field of the request; {@code field} is a JSON path such as {@code steps[1].to[0]}. */
    public record FieldProblem(String field, String code, String message) {
    }

    private final Kind kind;
    private final String code;
    private final List<FieldProblem> errors;

    public ApiException(Kind kind, String code, String message) {
        this(kind, code, message, List.of());
    }

    public ApiException(Kind kind, String code, String message, List<FieldProblem> errors) {
        super(message);
        this.kind = kind;
        this.code = code;
        this.errors = List.copyOf(errors);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(Kind.NOT_FOUND, code, message);
    }

    public static ApiException invalid(List<FieldProblem> errors) {
        return new ApiException(Kind.UNPROCESSABLE, "VALIDATION_FAILED", summary(errors), errors);
    }

    protected static String summary(List<FieldProblem> errors) {
        return errors.size() == 1 ? "1 field is invalid." : errors.size() + " fields are invalid.";
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return code;
    }

    public List<FieldProblem> errors() {
        return errors;
    }
}
