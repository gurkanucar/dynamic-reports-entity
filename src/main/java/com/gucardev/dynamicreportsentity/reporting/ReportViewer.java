package com.gucardev.dynamicreportsentity.reporting;

import java.util.Set;

/** The user a report is built for: drives field-level and row-level access. */
public record ReportViewer(String username, Set<String> authorities) {

    public ReportViewer {
        authorities = Set.copyOf(authorities);
    }

    public boolean hasAuthority(String authority) {
        return authorities.contains(authority);
    }
}
