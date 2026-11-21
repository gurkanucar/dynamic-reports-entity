package com.gucardev.dynamicreportsentity.platform.dataset.catalog;

import java.util.List;
import java.util.Map;

/**
 * An entity exposed to the dataset builder.
 *
 * @param idColumns    primary key column(s); more than one for composite keys (e.g. pure junction tables)
 * @param fields       dataset-ready fields by name
 * @param navigations  outgoing navigations by name (references and their inverses)
 * @param columns      column of every persistent field by field name (used by row restrictions)
 * @param ownerColumns for each owner entity name, the column on this entity that points at it
 */
public record CatalogEntity(String name, String label, String table, List<String> idColumns,
                               Map<String, CatalogField> fields, Map<String, Navigation> navigations,
                               Map<String, String> columns, Map<String, String> ownerColumns) {
}
