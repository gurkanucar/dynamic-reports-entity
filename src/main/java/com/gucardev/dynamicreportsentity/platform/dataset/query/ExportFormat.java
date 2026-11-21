package com.gucardev.dynamicreportsentity.platform.dataset.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** File formats a dataset is exported to. Excel is what business users open; CSV for other systems. */
public enum ExportFormat {

    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"),
    CSV("text/csv", ".csv");

    private final String contentType;
    private final String extension;

    ExportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    /** With the dot: {@code .xlsx}. */
    public String extension() {
        return extension;
    }

    @JsonValue
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** {@code xlsx} / {@code csv}, any case. */
    @JsonCreator
    public static ExportFormat of(String value) {
        return valueOf(value.strip().toUpperCase(Locale.ROOT));
    }
}
