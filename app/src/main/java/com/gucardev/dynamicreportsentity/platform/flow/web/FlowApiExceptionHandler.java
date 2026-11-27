package com.gucardev.dynamicreportsentity.platform.flow.web;

import com.gucardev.dynamicreportsentity.platform.api.ApiExceptionHandlerSupport;
import com.gucardev.dynamicreportsentity.platform.api.Problems;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.InvalidTypeIdException;

/** Errors of the flow API: the shared mapping plus the flow's own causes. */
@RestControllerAdvice(basePackageClasses = FlowApiExceptionHandler.class)
class FlowApiExceptionHandler extends ApiExceptionHandlerSupport {

    @Override
    protected ProblemDetail unreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidTypeIdException invalidType) {
                return Problems.of(HttpStatus.BAD_REQUEST, "UNKNOWN_STEP_TYPE", "Unknown or missing step type"
                        + (invalidType.getTypeId() == null ? "" : " '" + invalidType.getTypeId() + "'") + ".", request);
            }
        }
        return super.unreadable(exception, request);
    }

}
