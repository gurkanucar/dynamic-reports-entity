package com.gucardev.dynamicreportsentity.platform.approval;

import com.gucardev.dynamicreportsentity.platform.api.ApiException;

import java.util.List;

/** An {@link ApiException} of the approval API (codes in {@code api/errors.yaml}). */
public class ApprovalException extends ApiException {

    public ApprovalException(Kind kind, String code, String message) {
        super(kind, code, message);
    }

    private ApprovalException(List<FieldProblem> errors) {
        super(Kind.UNPROCESSABLE, "VALIDATION_FAILED", summary(errors), errors);
    }

    public static ApprovalException invalid(List<FieldProblem> errors) {
        return new ApprovalException(errors);
    }

    public static ApprovalException notFound(String code, String message) {
        return new ApprovalException(Kind.NOT_FOUND, code, message);
    }
}
