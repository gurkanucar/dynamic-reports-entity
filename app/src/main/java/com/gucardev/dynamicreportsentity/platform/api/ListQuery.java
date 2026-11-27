package com.gucardev.dynamicreportsentity.platform.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The list parameters of every paged endpoint (docs/REST API Tasarım Rehberi.md, 7): {@code sort=-createdAt,title}
 * against a whitelist of API field names (unknown ones answer 400, nothing reaches SQL as text; {@code id} is always
 * appended so pages never overlap), and {@code q}, a free-text filter of at least 2 characters.
 */
public final class ListQuery {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private ListQuery() {
    }

    /**
     * @param sort    the parameter as sent, null = {@code defaultSort}
     * @param allowed API field name → entity property
     */
    public static Sort sort(String sort, Map<String, String> allowed, String defaultSort) {
        String value = sort == null || sort.isBlank() ? defaultSort : sort;
        List<Sort.Order> orders = new ArrayList<>();
        for (String part : value.split(",")) {
            String field = part.strip();
            boolean descending = field.startsWith("-");
            String name = descending ? field.substring(1) : field;
            String property = allowed.get(name);
            if (property == null) {
                throw new ApiException(ApiException.Kind.BAD_REQUEST, "INVALID_SORT",
                        "Cannot sort by '" + name + "'; allowed: " + String.join(", ", allowed.keySet()) + ".");
            }
            orders.add(descending ? Sort.Order.desc(property) : Sort.Order.asc(property));
        }
        if (orders.stream().noneMatch(order -> order.getProperty().equals("id"))) {
            orders.add(Sort.Order.desc("id"));
        }
        return Sort.by(orders);
    }

    /** A 1-based page with the given order. */
    public static PageRequest page(int page, int size, Sort sort) {
        return PageRequest.of(page - 1, size, sort);
    }

    /** {@code q} as a lower-case LIKE pattern ({@code %text%}); null when not given. */
    public static String like(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String text = q.strip();
        if (text.length() < 2) {
            throw new ApiException(ApiException.Kind.BAD_REQUEST, "INVALID_PARAMETER", "q needs at least 2 characters.");
        }
        if (text.length() > 100) {
            throw new ApiException(ApiException.Kind.BAD_REQUEST, "INVALID_PARAMETER", "q is at most 100 characters.");
        }
        String escaped = text.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /** The start of a day ({@code createdFrom}); null stays null. */
    public static Instant from(LocalDate day) {
        return day == null ? null : day.atStartOfDay(ZONE).toInstant();
    }

    /** The end of a day, exclusive ({@code createdTo} includes the whole day). */
    public static Instant to(LocalDate day) {
        return day == null ? null : day.plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    /** 400 when {@code from} is after {@code to}. */
    public static void range(LocalDate from, LocalDate to, String name) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(ApiException.Kind.BAD_REQUEST, "INVALID_PARAMETER",
                    name + "From is after " + name + "To.");
        }
    }
}
