package com.gucardev.dynamicreportsentity.platform.api;

import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.Kind;

import java.util.List;
import java.util.function.Supplier;

/** Small helpers the admin (CRUD) APIs of the business modules share. */
public final class Admin {

    /** Who may manage master data (departments, personnel, projects, trainings). */
    public static final String AUTHORITY = "PLATFORM_ADMIN";

    private Admin() {
    }

    /** 412 when the record changed since the client read it (its ETag). */
    public static void requireVersion(long current, long expected) {
        if (current != expected) {
            throw versionMismatch();
        }
    }

    public static ApiException versionMismatch() {
        return new ApiException(Kind.PRECONDITION_FAILED, "VERSION_MISMATCH",
                "The record was changed by someone else; reload it and try again.");
    }

    /** 422 with every problem at once, if there are any. */
    public static void check(List<FieldProblem> problems) {
        if (!problems.isEmpty()) {
            throw ApiException.invalid(problems);
        }
    }

    public static Supplier<ApiException> notFound(String code, String what, long id) {
        return () -> new ApiException(Kind.NOT_FOUND, code, "There is no " + what + " " + id + ".");
    }

    /** 409 for a record others still use, naming them ("12 personel, 3 proje"). */
    public static ApiException inUse(String code, String what, List<String> users) {
        return new ApiException(Kind.CONFLICT, code, what + " is still used: " + String.join(", ", users) + ".");
    }

    public static void required(List<FieldProblem> problems, String field, Object value) {
        if (value == null || value instanceof String text && text.isBlank()) {
            problems.add(new FieldProblem(field, "REQUIRED", field + " is required."));
        }
    }

    public static void maxLength(List<FieldProblem> problems, String field, String value, int max) {
        if (value != null && value.length() > max) {
            problems.add(new FieldProblem(field, "INVALID_LENGTH", "At most " + max + " characters."));
        }
    }

    /** Trimmed, or null when empty. */
    public static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
