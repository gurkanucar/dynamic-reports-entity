package com.gucardev.dynamicreportsentity.platform.assistant;

import com.gucardev.entityreports.DatasetCatalog;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * The catalog as one user may use it, in a compact text for the model: entity, field and relation names with labels
 * and types. Only metadata, never values; fields the user may not use are left out, exactly like
 * {@code GET /api/datasets/catalog}.
 */
@Component
class CatalogDescriber {

    private final DatasetCatalog catalog;

    CatalogDescriber(DatasetCatalog catalog) {
        this.catalog = catalog;
    }

    String describe(Actor viewer) {
        StringBuilder text = new StringBuilder();
        for (DatasetCatalog.CatalogEntity entity : catalog.entities()) {
            text.append("Entity ").append(entity.name()).append(" — \"").append(entity.label()).append("\"\n");
            text.append("  fields: ").append(entity.fields().values().stream()
                    .filter(field -> field.isVisibleTo(viewer))
                    .map(CatalogDescriber::field)
                    .collect(Collectors.joining("; "))).append('\n');
            if (!entity.navigations().isEmpty()) {
                text.append("  relations: ").append(entity.navigations().values().stream()
                        .map(CatalogDescriber::relation)
                        .collect(Collectors.joining("; "))).append('\n');
            }
        }
        return text.toString();
    }

    private static String field(DatasetCatalog.CatalogField field) {
        return field.name() + " " + field.type() + (field.label().equalsIgnoreCase(field.name()) ? ""
                : " \"" + field.label() + "\"");
    }

    private static String relation(DatasetCatalog.Navigation navigation) {
        return navigation.name() + " -> " + navigation.targetEntity() + (navigation.toMany() ? " (to-many)" : " (to-one)")
                + " \"" + navigation.label() + "\"";
    }
}
