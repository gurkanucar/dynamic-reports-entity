package com.gucardev.dynamicreportsentity.personnel.internal;

import com.gucardev.dynamicreportsentity.personnel.Personnel;
import com.gucardev.dynamicreportsentity.reporting.ReportAccessDeniedException;
import com.gucardev.dynamicreportsentity.reporting.ReportRowPolicy;
import com.gucardev.dynamicreportsentity.reporting.ReportViewer;
import com.gucardev.dynamicreportsentity.reporting.RowRestriction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Row-level security: users only see personnel of their own department, unless they hold
 * {@link Personnel#ALL_DEPARTMENTS}. Entities declaring Personnel as {@code owner} (profiles,
 * skills, training records, project assignments) follow automatically.
 * <p>
 * The department is resolved from the user's personnel record here; with Keycloak it would
 * typically come from a token claim instead.
 */
@Component
@RequiredArgsConstructor
class DepartmentRowPolicy implements ReportRowPolicy {

    private final PersonnelRepository personnelRepository;

    @Override
    public List<RowRestriction> restrictionsFor(ReportViewer viewer) {
        if (viewer.hasAuthority(Personnel.ALL_DEPARTMENTS)) {
            return List.of();
        }
        Long departmentId = personnelRepository.findByUsername(viewer.username())
                .map(Personnel::getDepartmentId)
                .orElseThrow(() -> new ReportAccessDeniedException(
                        "No department is assigned to user " + viewer.username()));
        return List.of(new RowRestriction("Personnel", "departmentId", departmentId));
    }
}
