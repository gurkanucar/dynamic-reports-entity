package com.gucardev.dynamicreportsentity.platform.api;

import com.gucardev.dynamicreportsentity.platform.api.ApiException.Kind;

import java.time.Instant;
import java.util.Locale;

/** Conventions every platform controller shares: paging, ETags, snake_case enums, ISO times. */
public final class HttpSupport {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private HttpSupport() {
    }

    /** Validates {@code page} (1-based) and {@code size} (1-100); out of range is 400, never silently fixed. */
    public static int[] paging(Integer page, Integer size) {
        int pageNumber = page == null ? 1 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageNumber < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ApiException(Kind.BAD_REQUEST, "INVALID_PAGINATION",
                    "page must be at least 1 and size between 1 and " + MAX_PAGE_SIZE + ".");
        }
        return new int[]{pageNumber, pageSize};
    }

    public static String etag(long version) {
        return "\"" + version + "\"";
    }

    /** The version an {@code If-Match} header asks for: 428 without one, 412 if it is not one of ours. */
    public static long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ApiException(Kind.PRECONDITION_REQUIRED, "IF_MATCH_REQUIRED",
                    "Send the ETag of the version you edited in the If-Match header.");
        }
        String tag = ifMatch.strip();
        if (tag.startsWith("W/")) {
            tag = tag.substring(2);
        }
        tag = tag.replace("\"", "");
        try {
            return Long.parseLong(tag);
        } catch (NumberFormatException e) {
            throw new ApiException(Kind.PRECONDITION_FAILED, "VERSION_MISMATCH",
                    "The If-Match header does not match the current version.");
        }
    }

    /** Enum values travel as snake_case strings. */
    public static String value(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String value, String parameter) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(Kind.BAD_REQUEST, "INVALID_PARAMETER",
                    "Unknown value '" + value + "' for " + parameter + ".");
        }
    }

    public static String time(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
