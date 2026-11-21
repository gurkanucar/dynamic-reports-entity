package com.gucardev.dynamicreportsentity.platform.flow.web;

import com.gucardev.dynamicreportsentity.platform.api.Problems;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowException;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.reporting.ReportAccessDeniedException;
import com.gucardev.dynamicreportsentity.platform.reporting.query.InvalidReportException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.exc.InvalidTypeIdException;

import java.util.List;
import java.util.Map;

/**
 * Errors of the flow API as RFC 9457 problems with a stable {@code code} and a {@code traceId}
 * (docs/REST API Tasarım Rehberi.md, 6). Unexpected errors reveal nothing but the trace id.
 */
@Slf4j
@RestControllerAdvice(basePackageClasses = FlowApiExceptionHandler.class)
class FlowApiExceptionHandler {

    private static final Map<String, String> CONSTRAINT_CODES = Map.ofEntries(
            Map.entry("NotBlank", "REQUIRED"), Map.entry("NotNull", "REQUIRED"), Map.entry("NotEmpty", "REQUIRED"),
            Map.entry("Size", "INVALID_LENGTH"), Map.entry("Pattern", "INVALID_FORMAT"),
            Map.entry("Email", "INVALID_EMAIL"), Map.entry("Min", "OUT_OF_RANGE"), Map.entry("Max", "OUT_OF_RANGE"),
            Map.entry("Positive", "OUT_OF_RANGE"));

    @ExceptionHandler(FlowException.class)
    ProblemDetail flow(FlowException exception, HttpServletRequest request) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case GONE -> HttpStatus.GONE;
            case PRECONDITION_FAILED -> HttpStatus.PRECONDITION_FAILED;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case PRECONDITION_REQUIRED -> HttpStatus.PRECONDITION_REQUIRED;
        };
        return problem(status, exception.code(), exception.getMessage(), exception.errors(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldProblem> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldProblem(error.getField(),
                        CONSTRAINT_CODES.getOrDefault(error.getCode(), "INVALID_VALUE"), error.getDefaultMessage()))
                .toList();
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED",
                errors.size() == 1 ? "1 field is invalid." : errors.size() + " fields are invalid.", errors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidTypeIdException invalidType) {
                return problem(HttpStatus.BAD_REQUEST, "UNKNOWN_STEP_TYPE", "Unknown or missing step type"
                        + (invalidType.getTypeId() == null ? "" : " '" + invalidType.getTypeId() + "'")
                        + "; use one of: report, mail.", List.of(), request);
            }
        }
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body is not valid JSON or a value has the wrong type.", List.of(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Invalid value for '" + exception.getName() + "'.", List.of(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail mediaType(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Send the body as application/json.", List.of(), request);
    }

    /** A report definition that is valid JSON but cannot be built (outside of a flow save, e.g. a preview). */
    @ExceptionHandler(InvalidReportException.class)
    ProblemDetail invalidReport(InvalidReportException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_REPORT_DEFINITION", exception.getMessage(),
                List.of(), request);
    }

    @ExceptionHandler(ReportAccessDeniedException.class)
    ProblemDetail reportAccess(ReportAccessDeniedException exception, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "REPORT_FIELD_FORBIDDEN", exception.getMessage(), List.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "INSUFFICIENT_ROLE", "You are not allowed to do this.", List.of(),
                request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", null, List.of(), request);
        log.error("Unexpected error in {} {} (traceId {})", request.getMethod(), request.getRequestURI(),
                problem.getProperties().get("traceId"), exception);
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail, List<FieldProblem> errors,
                                         HttpServletRequest request) {
        return Problems.of(status, code, detail, errors, request);
    }
}
