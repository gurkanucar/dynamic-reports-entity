/**
 * Datasets module: a generic, self-service dataset builder.
 * <p>
 * Business modules expose data by annotating their own JPA entities with {@link DatasetEntity},
 * {@link DatasetField} and {@link DatasetReference} (the public API of this module). At startup the
 * catalog discovers those entities through the JPA metamodel, so this module never depends on
 * any business module. Business users pick fields and filters; the engine derives the JOINs from
 * the declared references and generates parameterised SQL.
 */
@ApplicationModule(id = "dataset", displayName = "Datasets")
package com.gucardev.dynamicreportsentity.platform.dataset;

import org.springframework.modulith.ApplicationModule;
