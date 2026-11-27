/**
 * Training module: the course catalog and who completed what. References personnel by ID ({@code @DatasetReference}
 * for datasets); the admin API checks the person through the personnel module and drops a deleted person's records
 * on {@link com.gucardev.dynamicreportsentity.personnel.PersonnelRemoved}.
 */
@ApplicationModule(displayName = "Training", allowedDependencies = {"api", "identity", "personnel"})
package com.gucardev.dynamicreportsentity.training;

import org.springframework.modulith.ApplicationModule;
