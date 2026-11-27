/**
 * Personnel module: people, their profiles and skills. Exposes its data to datasets through the datasets
 * annotations, and its admin API ({@code /api/personnel}, {@code /api/skills}). Deleting a person publishes
 * {@link com.gucardev.dynamicreportsentity.personnel.PersonnelRemoved} so other modules drop their rows.
 */
@ApplicationModule(displayName = "Personnel", allowedDependencies = {"identity", "api", "organization"})
package com.gucardev.dynamicreportsentity.personnel;

import org.springframework.modulith.ApplicationModule;
