package com.gucardev.dynamicreportsentity.platform.dataset.query;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * What the business user built in the UI. Fields are paths starting at {@code root}. Example –
 * departments with at least 3 active people, by average salary:
 * <pre>{@code
 * {
 *   "root": "Personnel",
 *   "columns": [ {"field": "department.name"},
 *                {"aggregate": "COUNT", "label": "Headcount"},
 *                {"field": "salary", "aggregate": "AVG"} ],
 *   "where":   {"field": "active", "operator": "EQ", "value": true},
 *   "having":  {"aggregate": "COUNT", "operator": "GTE", "value": 3},
 *   "sort":    [ {"field": "salary", "aggregate": "AVG", "direction": "DESC"} ]
 * }
 * }</pre>
 *
 * @param perRoot one row per root entity, with aggregates over one-to-many relations computed per row
 *                (null = automatic: on when the only aggregates are LISTs; false = always group by the columns)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatasetDefinition(@NotBlank String root,
                               @NotEmpty List<@NotNull @Valid DatasetColumn> columns,
                               @Valid DatasetCondition where,
                               @Valid DatasetCondition having,
                               List<@NotNull @Valid DatasetSort> sort,
                               Boolean perRoot) {

    public DatasetDefinition {
        sort = sort == null ? List.of() : sort;
    }

    public DatasetDefinition(String root, List<DatasetColumn> columns, DatasetCondition where, DatasetCondition having,
                            List<DatasetSort> sort) {
        this(root, columns, where, having, sort, null);
    }
}
