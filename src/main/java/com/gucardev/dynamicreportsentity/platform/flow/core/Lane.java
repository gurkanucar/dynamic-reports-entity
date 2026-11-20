package com.gucardev.dynamicreportsentity.platform.flow.core;

/**
 * Each lane has its own number of slots per instance, so long reports never hold up e-mails.
 */
public enum Lane {
    /** Database-heavy work: report queries and exports. */
    HEAVY,
    /** Short calls: e-mail, and later webhooks and events. */
    LIGHT
}
