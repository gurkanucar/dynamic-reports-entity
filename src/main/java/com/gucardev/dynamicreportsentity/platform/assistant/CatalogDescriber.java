package com.gucardev.dynamicreportsentity.platform.assistant;

import com.gucardev.dynamicreportsentity.platform.reporting.ReportViewer;
import com.gucardev.dynamicreportsentity.platform.reporting.catalog.CatalogField;
import com.gucardev.dynamicreportsentity.platform.reporting.catalog.Navigation;
import com.gucardev.dynamicreportsentity.platform.reporting.catalog.ReportCatalog;
import com.gucardev.dynamicreportsentity.platform.reporting.catalog.ReportableEntity;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * The catalog as one user may use it, in a compact text for the model: entity, field and relation names with labels
 * and types. Only metadata, never values; fields the user may not use are left out, exactly like
 * {@code GET /api/reports/catalog}.
 */
@Component
class CatalogDescriber {

    private final ReportCatalog catalog;

    CatalogDescriber(ReportCatalog catalog) {
        this.catalog = catalog;
    }

    String describe(ReportViewer viewer) {
        StringBuilder text = new StringBuilder();
        for (ReportableEntity entity : catalog.entities()) {
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

    private static String field(CatalogField field) {
        return field.name() + " " + field.type() + (field.label().equalsIgnoreCase(field.name()) ? ""
                : " \"" + field.label() + "\"");
    }

    private static String relation(Navigation navigation) {
        return navigation.name() + " -> " + navigation.targetEntity() + (navigation.toMany() ? " (to-many)" : " (to-one)")
                + " \"" + navigation.label() + "\"";
    }
}
