/**
 * Organization module: the department hierarchy, and its admin API ({@code /api/departments}). Other modules
 * plug in through {@link com.gucardev.dynamicreportsentity.organization.DepartmentUsage} (who still uses a
 * department) and {@link com.gucardev.dynamicreportsentity.organization.PersonDirectory} (does a manager exist).
 */
@ApplicationModule(displayName = "Organization", allowedDependencies = {"dataset", "api", "identity"})
package com.gucardev.dynamicreportsentity.organization;

import org.springframework.modulith.ApplicationModule;
