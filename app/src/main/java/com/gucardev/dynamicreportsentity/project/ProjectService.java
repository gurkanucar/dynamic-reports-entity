package com.gucardev.dynamicreportsentity.project;

import com.gucardev.dynamicreportsentity.organization.DepartmentService;
import com.gucardev.dynamicreportsentity.organization.DepartmentUsage;
import com.gucardev.dynamicreportsentity.personnel.PersonnelRemoved;
import com.gucardev.dynamicreportsentity.personnel.PersonnelService;
import com.gucardev.dynamicreportsentity.platform.api.Admin;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.api.ListQuery;
import org.springframework.data.domain.Page;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Projects and who works on them. */
@Service
public class ProjectService implements DepartmentUsage {

    public static final List<String> STATUSES = List.of("PLANNED", "ACTIVE", "ON_HOLD", "COMPLETED");

    public record ProjectInput(String code, String name, String status, BigDecimal budget, LocalDate startDate,
                               LocalDate endDate, Long departmentId) {
    }

    public record AssignmentInput(Long personnelId, String role, Integer allocationPercent, LocalDate assignedOn) {
    }

    public record Filter(String q, List<String> statuses, Long departmentId, BigDecimal budgetMin, BigDecimal budgetMax,
                         String sort) {
    }

    public record Listed(Project project, String departmentName, long assignments, long allocation) {
    }

    public record ListedAssignment(ProjectAssignment assignment, String personnelName) {
    }

    private static final Map<String, String> SORTS = Map.of("code", "code", "name", "name", "status", "status",
            "budget", "budget", "startDate", "startDate", "endDate", "endDate", "id", "id");
    private static final Map<String, String> ASSIGNMENT_SORTS = Map.of("role", "role",
            "allocationPercent", "allocationPercent", "assignedOn", "assignedOn", "id", "id");

    private final ProjectRepository projects;
    private final ProjectAssignmentRepository assignments;
    private final DepartmentService departments;
    private final PersonnelService personnel;

    ProjectService(ProjectRepository projects, ProjectAssignmentRepository assignments, DepartmentService departments,
                   PersonnelService personnel) {
        this.projects = projects;
        this.assignments = assignments;
        this.departments = departments;
        this.personnel = personnel;
    }

    @Transactional(readOnly = true)
    public Page<Listed> list(Filter filter, int page, int size) {
        Page<Project> found = projects.search(ListQuery.like(filter.q()), filter.statuses().isEmpty(),
                filter.statuses().isEmpty() ? List.of("-") : filter.statuses(), filter.departmentId(),
                filter.budgetMin(), filter.budgetMax(), ListQuery.page(page, size, ListQuery.sort(filter.sort(), SORTS, "code")));
        return decorate(found);
    }

    @Transactional(readOnly = true)
    public Listed get(long id) {
        return decorate(new org.springframework.data.domain.PageImpl<>(List.of(find(id)))).getContent().getFirst();
    }

    @Transactional
    public Project create(ProjectInput input) {
        validate(null, input);
        Project project = new Project(input.code().strip());
        apply(project, input);
        return projects.saveAndFlush(project);
    }

    @Transactional
    public Project update(long id, long expectedVersion, ProjectInput input) {
        Project project = find(id);
        Admin.requireVersion(project.getVersion(), expectedVersion);
        validate(id, input);
        apply(project, input);
        try {
            return projects.saveAndFlush(project);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw Admin.versionMismatch();
        }
    }

    /** Deletes the project with its assignments. */
    @Transactional
    public void delete(long id) {
        Project project = find(id);
        assignments.deleteByProject(id);
        projects.delete(project);
    }

    // ---- assignments ----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<ListedAssignment> assignments(long projectId, String sort, int page, int size) {
        find(projectId);
        Page<ProjectAssignment> found = assignments.findByProjectId(projectId,
                ListQuery.page(page, size, ListQuery.sort(sort, ASSIGNMENT_SORTS, "-allocationPercent")));
        Map<Long, String> names = personnel.names(found.getContent().stream().map(ProjectAssignment::getPersonnelId).toList());
        return found.map(item -> new ListedAssignment(item, names.get(item.getPersonnelId())));
    }

    @Transactional
    public ProjectAssignment assign(long projectId, AssignmentInput input) {
        find(projectId);
        validate(projectId, null, input);
        ProjectAssignment assignment = new ProjectAssignment(projectId, input.personnelId());
        assignment.define(input.personnelId(), Admin.text(input.role()), input.allocationPercent(),
                input.assignedOn() == null ? LocalDate.now() : input.assignedOn());
        return assignments.saveAndFlush(assignment);
    }

    @Transactional
    public ProjectAssignment updateAssignment(long id, long expectedVersion, AssignmentInput input) {
        ProjectAssignment assignment = findAssignment(id);
        Admin.requireVersion(assignment.getVersion(), expectedVersion);
        validate(assignment.getProjectId(), id, input);
        assignment.define(input.personnelId(), Admin.text(input.role()), input.allocationPercent(), input.assignedOn());
        try {
            return assignments.saveAndFlush(assignment);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw Admin.versionMismatch();
        }
    }

    @Transactional
    public void unassign(long id) {
        assignments.delete(findAssignment(id));
    }

    /** A person was deleted: their assignments go too. */
    @ApplicationModuleListener
    void on(PersonnelRemoved removed) {
        assignments.deleteByPersonnel(removed.personnelId());
    }

    // ---- DepartmentUsage --------------------------------------------------------------------------------

    @Override
    public String label() {
        return "proje";
    }

    @Override
    public Map<Long, Long> countByDepartment(Collection<Long> departmentIds) {
        Map<Long, Long> counts = new HashMap<>();
        projects.countByDepartment(departmentIds).forEach(row -> counts.put((Long) row[0], (Long) row[1]));
        return counts;
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private Project find(long id) {
        return projects.findById(id).orElseThrow(Admin.notFound("PROJECT_NOT_FOUND", "project", id));
    }

    private ProjectAssignment findAssignment(long id) {
        return assignments.findById(id).orElseThrow(Admin.notFound("ASSIGNMENT_NOT_FOUND", "assignment", id));
    }

    private void apply(Project project, ProjectInput input) {
        project.define(input.code().strip().toUpperCase(java.util.Locale.ROOT), Admin.text(input.name()),
                input.status(), input.budget(), input.startDate(), input.endDate(), input.departmentId());
    }

    private void validate(Long id, ProjectInput input) {
        List<FieldProblem> problems = new ArrayList<>();
        Admin.required(problems, "code", input.code());
        Admin.maxLength(problems, "code", input.code(), 40);
        Admin.required(problems, "name", input.name());
        Admin.maxLength(problems, "name", input.name(), 255);
        if (input.code() != null && !input.code().isBlank() && (id == null
                ? projects.existsByCodeIgnoreCase(input.code().strip())
                : projects.existsByCodeIgnoreCaseAndIdNot(input.code().strip(), id))) {
            problems.add(new FieldProblem("code", "CODE_TAKEN", "Another project has this code."));
        }
        if (input.status() == null || !STATUSES.contains(input.status())) {
            problems.add(new FieldProblem("status", "INVALID_VALUE", "status is one of " + STATUSES + "."));
        }
        if (input.budget() != null && input.budget().signum() < 0) {
            problems.add(new FieldProblem("budget", "OUT_OF_RANGE", "The budget cannot be negative."));
        }
        if (input.startDate() != null && input.endDate() != null && input.endDate().isBefore(input.startDate())) {
            problems.add(new FieldProblem("endDate", "OUT_OF_RANGE", "The end date is before the start date."));
        }
        if (input.departmentId() != null && !departments.exists(input.departmentId())) {
            problems.add(new FieldProblem("departmentId", "DEPARTMENT_NOT_FOUND", "The department does not exist."));
        }
        Admin.check(problems);
    }

    private void validate(long projectId, Long id, AssignmentInput input) {
        List<FieldProblem> problems = new ArrayList<>();
        if (input.personnelId() == null) {
            problems.add(new FieldProblem("personnelId", "REQUIRED", "Choose a person."));
        } else if (!personnel.exists(input.personnelId())) {
            problems.add(new FieldProblem("personnelId", "PERSONNEL_NOT_FOUND", "The person does not exist."));
        } else if (id == null ? assignments.existsByProjectIdAndPersonnelId(projectId, input.personnelId())
                : assignments.existsByProjectIdAndPersonnelIdAndIdNot(projectId, input.personnelId(), id)) {
            problems.add(new FieldProblem("personnelId", "ALREADY_ASSIGNED", "The person is already on this project."));
        }
        if (input.allocationPercent() != null && (input.allocationPercent() < 0 || input.allocationPercent() > 100)) {
            problems.add(new FieldProblem("allocationPercent", "OUT_OF_RANGE", "Allocation is 0-100 %."));
        }
        Admin.maxLength(problems, "role", input.role(), 255);
        Admin.check(problems);
    }

    private Page<Listed> decorate(Page<Project> found) {
        List<Long> ids = found.getContent().stream().map(Project::getId).toList();
        Map<Long, String> names = departments.names(found.getContent().stream().map(Project::getDepartmentId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
        Map<Long, long[]> summary = new HashMap<>();
        if (!ids.isEmpty()) {
            assignments.summarize(ids).forEach(row -> summary.put((Long) row[0],
                    new long[]{(Long) row[1], ((Number) row[2]).longValue()}));
        }
        return found.map(project -> {
            long[] values = summary.getOrDefault(project.getId(), new long[2]);
            return new Listed(project, names.get(project.getDepartmentId()), values[0], values[1]);
        });
    }

    static Set<String> statuses() {
        return Set.copyOf(STATUSES);
    }
}
