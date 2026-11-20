package com.gucardev.dynamicreportsentity.reporting.catalog;

import com.gucardev.dynamicreportsentity.reporting.ReportViewer;

/**
 * A reportable field of an entity.
 *
 * @param name      Java field name, used in paths ({@code manager.fullName})
 * @param label     business label
 * @param column    physical column name
 * @param type      coarse data type
 * @param authority authority required to use the field, or {@code null} if unrestricted
 */
public record CatalogField(String name, String label, String column, FieldType type, String authority) {

    public boolean isVisibleTo(ReportViewer viewer) {
        return authority == null || viewer.hasAuthority(authority);
    }
}
