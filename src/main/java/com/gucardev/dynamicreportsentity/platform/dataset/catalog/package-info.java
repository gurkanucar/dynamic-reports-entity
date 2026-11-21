/**
 * The dataset catalog: entities, fields and relations available for datasets. Exposed as the
 * {@code datasets :: catalog} named interface so other platform modules (e.g. the dataset assistant) can describe
 * what a user may dataset on; {@link com.gucardev.dynamicreportsentity.platform.dataset.catalog.CatalogField#isVisibleTo}
 * decides per user.
 */
@NamedInterface("catalog")
package com.gucardev.dynamicreportsentity.platform.dataset.catalog;

import org.springframework.modulith.NamedInterface;
