package com.gucardev.dynamicreportsentity.platform.dataset.query;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Sort by a field path, optionally aggregated or date-grouped (must then match a grouped column);
 * {@code direction} defaults to ascending.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatasetSort(String field, Aggregate aggregate, Direction direction, DateGroup dateGroup) {

    public enum Direction { ASC, DESC }

    public DatasetSort {
        direction = direction == null ? Direction.ASC : direction;
    }

    public DatasetSort(String field, Aggregate aggregate, Direction direction) {
        this(field, aggregate, direction, null);
    }

    public static DatasetSort asc(String field) {
        return new DatasetSort(field, null, Direction.ASC, null);
    }

    public static DatasetSort desc(String field) {
        return new DatasetSort(field, null, Direction.DESC, null);
    }
}
