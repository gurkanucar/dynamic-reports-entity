/**
 * Training module. References personnel by ID only, so it does not depend on the personnel module;
 * the link is declared for datasets purposes with {@code @DatasetReference}.
 */
@ApplicationModule(displayName = "Training", allowedDependencies = "dataset")
package com.gucardev.dynamicreportsentity.training;

import org.springframework.modulith.ApplicationModule;
