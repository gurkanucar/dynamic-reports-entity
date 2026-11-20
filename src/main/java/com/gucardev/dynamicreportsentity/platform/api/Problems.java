package com.gucardev.dynamicreportsentity.platform.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * RFC 9457 problems as every platform API returns them: a stable {@code code}, a {@code traceId} (the
 * {@code X-Request-Id} header when the client sent one) and optional field errors
 * (docs/REST API Tasarım Rehberi.md, 6).
 */
public final class Problems {

    /** One invalid field; {@code field} is a JSON path such as {@code steps[1].to[0]}. */
    public record FieldError(String field, String code, String message) {
    }

    private Problems() {
    }

    public static ProblemDetail of(HttpStatus status, String code, String detail, HttpServletRequest request) {
        return of(status, code, detail, List.of(), request);
    }

    public static ProblemDetail of(HttpStatus status, String code, String detail, List<?> errors,
                                   HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create("/problems/" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(title(status));
        problem.setDetail(detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("traceId", traceId(request));
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }

    private static String title(HttpStatus status) {
        return switch (status) {
            case UNPROCESSABLE_CONTENT -> "Validation failed";
            case NOT_FOUND -> "Not found";
            case CONFLICT -> "Conflict with the current state";
            case FORBIDDEN -> "Forbidden";
            case PRECONDITION_FAILED -> "The record has changed";
            case PRECONDITION_REQUIRED -> "Version required";
            case GONE -> "No longer available";
            case SERVICE_UNAVAILABLE -> "Temporarily unavailable";
            case BAD_GATEWAY -> "Upstream service failed";
            case INTERNAL_SERVER_ERROR -> "Unexpected error";
            default -> "Bad request";
        };
    }

    private static String traceId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        return requestId != null && requestId.matches("[\\w-]{1,64}") ? requestId
                : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
