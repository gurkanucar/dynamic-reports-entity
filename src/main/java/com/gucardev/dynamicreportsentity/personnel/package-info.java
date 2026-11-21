/**
 * Personnel module. Exposes its data to reports only through the reporting annotations.
 */
@ApplicationModule(displayName = "Personnel", allowedDependencies = {"reporting", "identity"})
package com.gucardev.dynamicreportsentity.personnel;

import org.springframework.modulith.ApplicationModule;
