package com.gucardev.dynamicreportsentity.platform.flow.spi;

/**
 * Each lane has its own number of slots per instance, so long datasets never hold up e-mails.
 */
public enum Lane {
    /** Database-heavy work: dataset queries and exports. */
    HEAVY,
    /** Short calls: e-mail, and later webhooks and events. */
    LIGHT
}
