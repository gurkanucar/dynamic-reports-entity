package com.gucardev.dynamicreportsentity.platform.dataset.query;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;

import java.util.List;

/**
 * A node of a condition tree: a group, a predicate, a field comparison or an EXISTS test.
 * <pre>{@code
 * group:        {"logic": "OR", "conditions": [ ... ]}                     (logic defaults to AND)
 * predicate:    {"field": "salary", "operator": "GT", "value": 50000}
 * date part:    {"field": "hireDate", "dateGroup": "YEAR", "operator": "GTE", "value": 2020}
 * in having:    {"field": "salary", "aggregate": "AVG", "operator": "GT", "value": 50000}
 * two fields:   {"field": "personnel.department.id", "operator": "NOT_EQ", "otherField": "project.department.id"}
 * vs aggregate: {"field": "salary", "operator": "GT", "otherField": "department.members.salary", "otherAggregate": "AVG"}
 * exists:       {"relation": "trainingRecords", "operator": "NOT_EXISTS",
 *                "match": {"field": "training.name", "operator": "EQ", "value": "Fire Safety"}}
 * }</pre>
 *
 * @param otherField     compare with this field instead of a value (EQ, NOT_EQ, LT, LTE, GT, GTE)
 * @param otherAggregate aggregate the other field first: over a related group when its path crosses a
 *                       one-to-many relation (salary vs. its department's average), over the whole root
 *                       table when it is a plain root field (salary vs. the company average)
 * @param otherWhere     filters the rows the other aggregate runs over (relative to that group / the root)
 * @param relation       EXISTS / NOT_EXISTS: path to a one-to-many relation, e.g. {@code trainingRecords}
 * @param match          EXISTS / NOT_EXISTS: optional condition on the related rows, relative to the relation
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatasetCondition(Logic logic,
                              List<@Valid DatasetCondition> conditions,
                              String field,
                              Aggregate aggregate,
                              FilterOperator operator,
                              Object value,
                              DateGroup dateGroup,
                              String otherField,
                              Aggregate otherAggregate,
                              @Valid DatasetCondition otherWhere,
                              String relation,
                              @Valid DatasetCondition match) {

    public enum Logic { AND, OR }

    @JsonIgnore
    public boolean isGroup() {
        return conditions != null;
    }

    public static DatasetCondition and(DatasetCondition... conditions) {
        return new DatasetCondition(Logic.AND, List.of(conditions), null, null, null, null, null, null, null, null, null, null);
    }

    public static DatasetCondition or(DatasetCondition... conditions) {
        return new DatasetCondition(Logic.OR, List.of(conditions), null, null, null, null, null, null, null, null, null, null);
    }

    public static DatasetCondition where(String field, FilterOperator operator, Object value) {
        return new DatasetCondition(null, null, field, null, operator, value, null, null, null, null, null, null);
    }

    public static DatasetCondition where(String field, DateGroup dateGroup, FilterOperator operator, Object value) {
        return new DatasetCondition(null, null, field, null, operator, value, dateGroup, null, null, null, null, null);
    }

    public static DatasetCondition where(Aggregate aggregate, String field, FilterOperator operator, Object value) {
        return new DatasetCondition(null, null, field, aggregate, operator, value, null, null, null, null, null, null);
    }

    public static DatasetCondition compare(String field, FilterOperator operator, String otherField) {
        return new DatasetCondition(null, null, field, null, operator, null, null, otherField, null, null, null, null);
    }

    /** {@code field <operator> aggregate(otherField)}, e.g. salary above the department average. */
    public static DatasetCondition compare(String field, FilterOperator operator, Aggregate otherAggregate, String otherField,
                                          DatasetCondition otherWhere) {
        return new DatasetCondition(null, null, field, null, operator, null, null, otherField, otherAggregate, otherWhere,
                null, null);
    }

    public static DatasetCondition exists(String relation, DatasetCondition match) {
        return new DatasetCondition(null, null, null, null, FilterOperator.EXISTS, null, null, null, null, null, relation, match);
    }

    public static DatasetCondition notExists(String relation, DatasetCondition match) {
        return new DatasetCondition(null, null, null, null, FilterOperator.NOT_EXISTS, null, null, null, null, null, relation, match);
    }
}
