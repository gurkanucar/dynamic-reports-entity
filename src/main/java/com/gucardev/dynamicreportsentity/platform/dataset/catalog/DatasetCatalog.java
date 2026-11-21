package com.gucardev.dynamicreportsentity.platform.dataset.catalog;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The "dataset map": which entities, fields and navigations are available for datasets.
 * <p>
 * Built once at startup from the annotations on the entities (see {@link CatalogScanner}).
 * In a production setup this could be persisted to catalog tables so that a data steward can
 * adjust labels and permissions from a UI.
 */
public final class DatasetCatalog {

    private final Map<String, CatalogEntity> entities;

    DatasetCatalog(List<CatalogEntity> entities) {
        Map<String, CatalogEntity> byName = new LinkedHashMap<>();
        entities.forEach(entity -> byName.put(entity.name(), entity));
        this.entities = Collections.unmodifiableMap(byName);
    }

    /** Builds a catalog from the given classes; classes without {@code @DatasetEntity} are ignored. */
    public static DatasetCatalog of(Collection<Class<?>> candidates) {
        return CatalogScanner.scan(candidates);
    }

    public List<CatalogEntity> entities() {
        return List.copyOf(entities.values());
    }

    public Optional<CatalogEntity> entity(String name) {
        return Optional.ofNullable(entities.get(name));
    }
}
