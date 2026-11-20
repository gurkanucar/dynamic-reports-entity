package com.gucardev.dynamicreportsentity.platform.reporting;

import java.util.Set;

/**
 * The user a report is built for: drives field-level and row-level access.
 *
 * @param system the engine itself (snapshot refreshes): every field, no row restrictions. Never
 *               created from a request; see {@link #system(String)}.
 */
public record ReportViewer(String username, Set<String> authorities, boolean system) {

    public ReportViewer {
        authorities = Set.copyOf(authorities);
    }

    public ReportViewer(String username, Set<String> authorities) {
        this(username, authorities, false);
    }

    /** The engine refreshing a snapshot: sees all rows, so the snapshot must be guarded when it is read. */
    public static ReportViewer system(String purpose) {
        return new ReportViewer("system:" + purpose, Set.of(), true);
    }

    public boolean hasAuthority(String authority) {
        return system || authorities.contains(authority);
    }
}
