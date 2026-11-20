package com.gucardev.dynamicreportsentity.platform.reporting.dataset;

import com.gucardev.dynamicreportsentity.platform.reporting.query.ReportDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Creates or replaces a dataset.
 * <pre>{@code
 * {"name": "department_headcount", "label": "Headcount per department",
 *  "mode": "SNAPSHOT", "refreshEveryHours": 6,
 *  "definition": {"root": "Personnel", "columns": [ ... ]}}
 * }</pre>
 *
 * @param name              lower case letters, digits and underscores ({@code department_headcount}); ignored on update
 * @param refreshEveryHours SNAPSHOT only: 1 to 168 (e.g. 3, 6, 12, 24); empty = refreshed on demand only
 */
public record DatasetRequest(String name,
                             @Size(max = 255) String label,
                             @Size(max = 1000) String description,
                             @NotNull DatasetMode mode,
                             @Min(1) @Max(168) Integer refreshEveryHours,
                             @NotNull @Valid ReportDefinition definition) {
}
