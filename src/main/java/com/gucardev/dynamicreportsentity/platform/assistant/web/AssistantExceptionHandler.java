package com.gucardev.dynamicreportsentity.platform.assistant.web;

import com.gucardev.dynamicreportsentity.platform.api.Problems;
import com.gucardev.dynamicreportsentity.platform.assistant.AssistantException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/** Problems of the assistant API (RFC 9457 with {@code code} and {@code traceId}). */
@Slf4j
@RestControllerAdvice(basePackageClasses = AssistantExceptionHandler.class)
class AssistantExceptionHandler {

    @ExceptionHandler(AssistantException.class)
    ProblemDetail assistant(AssistantException exception, HttpServletRequest request) {
        return Problems.of(HttpStatus.valueOf(exception.status()), exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<Problems.FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new Problems.FieldError(error.getField(), "INVALID_VALUE", error.getDefaultMessage()))
                .toList();
        return Problems.of(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED",
                errors.size() + " field(s) are invalid.", errors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadable(HttpServletRequest request) {
        return Problems.of(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body is not valid JSON or a value has the wrong type.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied(HttpServletRequest request) {
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
