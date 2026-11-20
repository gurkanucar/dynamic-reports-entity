package com.gucardev.dynamicreportsentity.reporting.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

/** Coarse data type of a report field; drives which filters and editors the UI offers. */
public enum FieldType {
    TEXT, NUMBER, DATE, DATE_TIME, BOOLEAN;

    static FieldType of(Class<?> javaType) {
        if (javaType == LocalDate.class) {
            return DATE;
        }
        if (javaType == LocalDateTime.class || javaType == Instant.class
                || javaType == OffsetDateTime.class || javaType == ZonedDateTime.class) {
            return DATE_TIME;
        }
        if (javaType == Boolean.class || javaType == boolean.class) {
            return BOOLEAN;
        }
        if (Number.class.isAssignableFrom(javaType)
                || (javaType.isPrimitive() && javaType != char.class)) {
            return NUMBER;
        }
        return TEXT;
    }
}
