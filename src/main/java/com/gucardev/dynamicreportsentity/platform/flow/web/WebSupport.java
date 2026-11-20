package com.gucardev.dynamicreportsentity.platform.flow.web;

import com.gucardev.dynamicreportsentity.platform.flow.core.FlowException;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowException.Kind;
import com.gucardev.dynamicreportsentity.platform.reporting.ReportViewer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/** Small helpers shared by the flow controllers. */
final class WebSupport {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private WebSupport() {
    }

    static ReportViewer viewer(Authentication authentication) {
        return new ReportViewer(authentication.getName(), authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
    }

    /** Enum values travel as snake_case strings. */
    static String value(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    static <E extends Enum<E>> E parse(Class<E> type, String value, String parameter) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new FlowException(Kind.BAD_REQUEST, "INVALID_PARAMETER",
                    "Unknown value '" + value + "' for " + parameter + ".");
        }
    }

    /** Validates {@code page} (1-based) and {@code size} (1-100); out of range is 400, never silently fixed. */
    static int[] paging(Integer page, Integer size) {
        int pageNumber = page == null ? 1 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageNumber < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new FlowException(Kind.BAD_REQUEST, "INVALID_PAGINATION",
                    "page must be at least 1 and size between 1 and " + MAX_PAGE_SIZE + ".");
        }
        return new int[]{pageNumber, pageSize};
    }

    static String etag(long version) {
        return "\"" + version + "\"";
    }

    /** The version an {@code If-Match} header asks for: 428 without one, 412 if it is not one of ours. */
    static long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new FlowException(Kind.PRECONDITION_REQUIRED, "IF_MATCH_REQUIRED",
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
            throw new FlowException(Kind.PRECONDITION_FAILED, "VERSION_MISMATCH",
                    "The If-Match header does not match the current version.");
        }
    }

    static String time(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
