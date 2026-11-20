/**
 * The report catalog: entities, fields and relations available for reporting. Exposed as the
 * {@code reporting :: catalog} named interface so other platform modules (e.g. the report assistant) can describe
 * what a user may report on; {@link com.gucardev.dynamicreportsentity.platform.reporting.catalog.CatalogField#isVisibleTo}
 * decides per user.
 */
@NamedInterface("catalog")
package com.gucardev.dynamicreportsentity.platform.reporting.catalog;

import org.springframework.modulith.NamedInterface;
