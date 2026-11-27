package com.gucardev.dynamicreportsentity.personnel.internal;

import com.gucardev.entityreports.Dataset;
import com.gucardev.dynamicreportsentity.personnel.Personnel;
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
class DepartmentRowPolicy implements Dataset.RowPolicy {

    private final PersonnelRepository personnelRepository;

    @Override
    public List<Dataset.RowRestriction> restrictionsFor(Dataset.Viewer viewer) {
        if (viewer.hasAuthority(Personnel.ALL_DEPARTMENTS)) {
            return List.of();
        }
        Long departmentId = personnelRepository.findByUsername(viewer.username())
                .map(Personnel::getDepartmentId)
                .orElseThrow(() -> new Dataset.AccessDeniedException(
                        "No department is assigned to user " + viewer.username()));
        return List.of(new Dataset.RowRestriction("Personnel", "departmentId", departmentId));
    }
}
