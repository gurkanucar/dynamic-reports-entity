package com.gucardev.dynamicreportsentity.platform.api;

import java.util.UUID;

/**
 * Record ids are UUIDs (version 7: time-ordered, not guessable). Values that come from outside (JSON drafts, step
 * outputs, text columns) are parsed here; anything that is not a UUID is no id at all.
 */
public final class Ids {

    /** A UUID in text, for path patterns. */
    public static final String PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private Ids() {
    }

    /** The UUID {@code value} holds (a UUID or its text), or null. */
    public static UUID parse(Object value) {
        if (value instanceof UUID id) {
            return id;
        }
        if (!(value instanceof String text) || text.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
