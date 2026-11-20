package com.gucardev.dynamicreportsentity.reporting.query;

import jakarta.validation.Valid;

import java.util.List;

/**
 * A node of a condition tree: either a group or a single predicate.
 * <pre>{@code
 * group:     {"logic": "OR", "conditions": [ ... ]}            (logic defaults to AND)
 * predicate: {"field": "salary", "operator": "GT", "value": 50000}
 * date part: {"field": "hireDate", "dateGroup": "YEAR", "operator": "GTE", "value": 2020}
 * in having: {"field": "salary", "aggregate": "AVG", "operator": "GT", "value": 50000}
 * }</pre>
 */
public record ReportCondition(Logic logic,
                              List<@Valid ReportCondition> conditions,
                              String field,
                              Aggregate aggregate,
                              FilterOperator operator,
                              Object value,
                              DateGroup dateGroup) {

    public enum Logic { AND, OR }

    public boolean isGroup() {
        return conditions != null;
    }

    public static ReportCondition and(ReportCondition... conditions) {
        return new ReportCondition(Logic.AND, List.of(conditions), null, null, null, null, null);
    }

    public static ReportCondition or(ReportCondition... conditions) {
        return new ReportCondition(Logic.OR, List.of(conditions), null, null, null, null, null);
    }

    public static ReportCondition where(String field, FilterOperator operator, Object value) {
        return new ReportCondition(null, null, field, null, operator, value, null);
    }

    public static ReportCondition where(String field, DateGroup dateGroup, FilterOperator operator, Object value) {
        return new ReportCondition(null, null, field, null, operator, value, dateGroup);
    }

    public static ReportCondition where(Aggregate aggregate, String field, FilterOperator operator, Object value) {
        return new ReportCondition(null, null, field, aggregate, operator, value, null);
    }
}
