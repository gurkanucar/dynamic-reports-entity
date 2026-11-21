package com.gucardev.dynamicreportsentity.platform.dataset.query;

import com.gucardev.dynamicreportsentity.platform.dataset.catalog.FieldType;

import java.util.List;

/**
 * Header of an output column: what was asked for, its display label and the type of its values.
 * With {@code dateGroup} = MONTH/QUARTER the values are period start dates, for the UI to format.
 *
 * @param keys      LIST of objects: the keys of each object, else null
 * @param listLimit LIST: max elements per cell, else null
 */
public record ResultColumn(String field, Aggregate aggregate, DateGroup dateGroup, String label, FieldType type,
                           List<String> keys, Integer listLimit) {
}
