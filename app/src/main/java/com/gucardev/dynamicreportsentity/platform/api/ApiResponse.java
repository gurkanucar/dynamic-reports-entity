package com.gucardev.dynamicreportsentity.platform.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * The success envelope of the flow API: {@code {"data": ..., "meta": ...}} (docs/REST API Tasarım Rehberi.md, 5).
 */
public record ApiResponse<T>(T data, Object meta) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }

    /** Offset pages; {@code page} is 1-based. */
    public record PageMeta(int page, int size, long totalItems, int totalPages) {
    }

    public static <E, T> ApiResponse<List<T>> page(Page<E> page, Function<E, T> mapper) {
        return new ApiResponse<>(page.getContent().stream().map(mapper).toList(),
                new PageMeta(page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages()));
    }
}
