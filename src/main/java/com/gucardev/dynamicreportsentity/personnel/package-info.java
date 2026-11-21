/**
 * Personnel module. Exposes its data to datasets only through the datasets annotations.
 */
@ApplicationModule(displayName = "Personnel", allowedDependencies = {"dataset", "identity"})
package com.gucardev.dynamicreportsentity.personnel;

import org.springframework.modulith.ApplicationModule;
