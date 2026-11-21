package com.gucardev.dynamicreportsentity.platform.reporting;

import com.gucardev.dynamicreportsentity.platform.identity.Actor;

import java.util.List;

/**
 * Row-level security hook. The module that owns the data knows the rules (e.g. "users only see
 * their own department"), so it provides an implementation as a Spring bean; the report engine
 * applies the returned restrictions to every query.
 */
public interface ReportRowPolicy {

    /**
     * @return restrictions to enforce for this viewer; empty for unrestricted access
     * @throws ReportAccessDeniedException if the viewer may not see any rows at all
     */
    List<RowRestriction> restrictionsFor(Actor viewer);
}
