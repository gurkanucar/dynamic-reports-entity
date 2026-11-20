/**
 * Training module. References personnel by ID only, so it does not depend on the personnel module;
 * the link is declared for reporting purposes with {@code @ReportReference}.
 */
@ApplicationModule(displayName = "Training", allowedDependencies = "reporting")
package com.gucardev.dynamicreportsentity.training;

import org.springframework.modulith.ApplicationModule;
