package com.gucardev.dynamicreportsentity.reporting.catalog;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The "report map": which entities, fields and navigations are available for reporting.
 * <p>
 * Built once at startup from the annotations on the entities (see {@link CatalogScanner}).
 * In a production setup this could be persisted to catalog tables so that a data steward can
 * adjust labels and permissions from a UI.
 */
public final class ReportCatalog {

    private final Map<String, ReportableEntity> entities;

    ReportCatalog(List<ReportableEntity> entities) {
        Map<String, ReportableEntity> byName = new LinkedHashMap<>();
        entities.forEach(entity -> byName.put(entity.name(), entity));
        this.entities = Collections.unmodifiableMap(byName);
    }

    /** Builds a catalog from the given classes; classes without {@code @Reportable} are ignored. */
    public static ReportCatalog of(Collection<Class<?>> candidates) {
        return CatalogScanner.scan(candidates);
    }

    public List<ReportableEntity> entities() {
        return List.copyOf(entities.values());
    }

    public Optional<ReportableEntity> entity(String name) {
        return Optional.ofNullable(entities.get(name));
    }
}
