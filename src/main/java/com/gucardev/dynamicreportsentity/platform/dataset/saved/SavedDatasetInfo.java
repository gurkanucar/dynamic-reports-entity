package com.gucardev.dynamicreportsentity.platform.dataset.saved;

import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetDefinition;

import java.time.Instant;

/** A saved dataset as other modules and the API see it. */
public record SavedDatasetInfo(String name, String label, String description, DatasetDefinition definition,
                               long version, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
}
