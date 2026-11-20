package com.gucardev.dynamicreportsentity.reporting.query;

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
public record ReportDefinition(@NotBlank String root,
                               @NotEmpty List<@NotNull @Valid ReportColumn> columns,
                               @Valid ReportCondition where,
                               @Valid ReportCondition having,
                               List<@NotNull @Valid ReportSort> sort,
                               Boolean perRoot) {

    public ReportDefinition {
        sort = sort == null ? List.of() : sort;
    }

    public ReportDefinition(String root, List<ReportColumn> columns, ReportCondition where, ReportCondition having,
                            List<ReportSort> sort) {
        this(root, columns, where, having, sort, null);
    }
}
