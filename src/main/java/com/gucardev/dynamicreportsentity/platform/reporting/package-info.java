/**
 * Reporting module: a generic, self-service report builder.
 * <p>
 * Business modules expose data by annotating their own JPA entities with {@link Reportable},
 * {@link ReportField} and {@link ReportReference} (the public API of this module). At startup the
 * catalog discovers those entities through the JPA metamodel, so this module never depends on
 * any business module. Business users pick fields and filters; the engine derives the JOINs from
 * the declared references and generates parameterised SQL.
 */
@ApplicationModule(id = "reporting", displayName = "Reporting")
package com.gucardev.dynamicreportsentity.platform.reporting;

import org.springframework.modulith.ApplicationModule;
