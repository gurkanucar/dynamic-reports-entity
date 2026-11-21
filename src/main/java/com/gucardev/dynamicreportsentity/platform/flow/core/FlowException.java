package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.api.ApiException;

import java.util.List;

/** An {@link ApiException} of the flow API (codes in {@code api/errors.yaml}). */
public class FlowException extends ApiException {

    public FlowException(Kind kind, String code, String message) {
        super(kind, code, message);
    }

    public FlowException(Kind kind, String code, String message, List<FieldProblem> errors) {
        super(kind, code, message, errors);
    }

    public static FlowException notFound(String code, String message) {
        return new FlowException(Kind.NOT_FOUND, code, message);
    }

    public static FlowException invalid(List<FieldProblem> errors) {
        return new FlowException(Kind.UNPROCESSABLE, "VALIDATION_FAILED", summary(errors), errors);
    }

    public static FlowException invalid(String field, String code, String message) {
        return invalid(List.of(new FieldProblem(field, code, message)));
    }
}
