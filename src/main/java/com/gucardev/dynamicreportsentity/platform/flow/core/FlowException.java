package com.gucardev.dynamicreportsentity.platform.flow.core;

import java.util.List;

/**
 * A request to the flow API that cannot be served. {@link Kind} decides the HTTP status, {@code code} is the
 * stable machine-readable error code (see {@code api/flows-api.yaml}), {@code errors} lists field problems.
 */
public class FlowException extends RuntimeException {

    public enum Kind {
        BAD_REQUEST, FORBIDDEN, NOT_FOUND, CONFLICT, GONE, PRECONDITION_FAILED, UNPROCESSABLE, PRECONDITION_REQUIRED
    }

    /** A problem with one field of the request; {@code field} is a JSON path such as {@code steps[1].to[0]}. */
    public record FieldProblem(String field, String code, String message) {
    }

    private final Kind kind;
    private final String code;
    private final List<FieldProblem> errors;

    public FlowException(Kind kind, String code, String message) {
        this(kind, code, message, List.of());
    }

    public FlowException(Kind kind, String code, String message, List<FieldProblem> errors) {
        super(message);
        this.kind = kind;
        this.code = code;
        this.errors = List.copyOf(errors);
    }

    public static FlowException notFound(String code, String message) {
        return new FlowException(Kind.NOT_FOUND, code, message);
    }

    public static FlowException invalid(List<FieldProblem> errors) {
        return new FlowException(Kind.UNPROCESSABLE, "VALIDATION_FAILED",
                errors.size() == 1 ? "1 field is invalid." : errors.size() + " fields are invalid.", errors);
    }

    public static FlowException invalid(String field, String code, String message) {
        return invalid(List.of(new FieldProblem(field, code, message)));
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
