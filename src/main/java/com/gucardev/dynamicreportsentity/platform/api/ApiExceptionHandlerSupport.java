package com.gucardev.dynamicreportsentity.platform.api;

import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

/**
 * The error mapping every platform API shares (docs/REST API Tasarım Rehberi.md, 6): RFC 9457 problems with a stable
 * {@code code} and a {@code traceId}. Each module scopes it to its own controllers:
 * {@code @RestControllerAdvice(basePackageClasses = ...) class XExceptionHandler extends ApiExceptionHandlerSupport}
 * and adds handlers for its own exceptions. Unexpected errors reveal nothing but the trace id.
 */
public abstract class ApiExceptionHandlerSupport {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandlerSupport.class);

    private static final Map<String, String> CONSTRAINT_CODES = Map.ofEntries(
            Map.entry("NotBlank", "REQUIRED"), Map.entry("NotNull", "REQUIRED"), Map.entry("NotEmpty", "REQUIRED"),
            Map.entry("Size", "INVALID_LENGTH"), Map.entry("Pattern", "INVALID_FORMAT"),
            Map.entry("Email", "INVALID_EMAIL"), Map.entry("Min", "OUT_OF_RANGE"), Map.entry("Max", "OUT_OF_RANGE"),
            Map.entry("Positive", "OUT_OF_RANGE"));

    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException exception, HttpServletRequest request) {
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
        return Problems.of(status, exception.code(), exception.getMessage(), exception.errors(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldProblem> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldProblem(error.getField(),
                        CONSTRAINT_CODES.getOrDefault(error.getCode(), "INVALID_VALUE"), error.getDefaultMessage()))
                .toList();
        return Problems.of(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", ApiException.summary(errors), errors,
                request);
    }

    /** Overridable: a module may recognise its own causes (e.g. an unknown step type). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    protected ProblemDetail unreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return Problems.of(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body is not valid JSON or a value has the wrong type.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return Problems.of(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Invalid value for '" + exception.getName() + "'.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail mediaType(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return Problems.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Send the body as application/json.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return Problems.of(HttpStatus.FORBIDDEN, "INSUFFICIENT_ROLE", "You are not allowed to do this.", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception, HttpServletRequest request) {
        ProblemDetail problem = Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", null, request);
        log.error("Unexpected error in {} {} (traceId {})", request.getMethod(), request.getRequestURI(),
                problem.getProperties().get("traceId"), exception);
        return problem;
    }
}
