package com.gucardev.dynamicreportsentity.reporting.query;

import com.gucardev.dynamicreportsentity.reporting.catalog.FieldType;

/**
 * Header of an output column: what was asked for, its display label and the type of its values.
 * With {@code dateGroup} = MONTH/QUARTER the values are period start dates, for the UI to format.
 */
public record ResultColumn(String field, Aggregate aggregate, DateGroup dateGroup, String label, FieldType type) {
}
