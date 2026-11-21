package com.gucardev.dynamicreportsentity.platform.dataset.saved;

import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Creates or replaces a saved dataset.
 *
 * @param name lower case letters, digits and underscores ({@code department_headcount}); ignored on update
 */
public record SavedDatasetRequest(String name,
                                  @Size(max = 255) String label,
                                  @Size(max = 1000) String description,
                                  @NotNull @Valid DatasetDefinition definition) {
}
