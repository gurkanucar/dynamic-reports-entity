package com.gucardev.dynamicreportsentity.project.web;

import com.gucardev.dynamicreportsentity.platform.api.ApiExceptionHandlerSupport;
import com.gucardev.dynamicreportsentity.platform.api.ApiResponse;
import com.gucardev.dynamicreportsentity.project.Project;
import com.gucardev.dynamicreportsentity.project.ProjectAssignment;
import com.gucardev.dynamicreportsentity.project.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.etag;
import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.expectedVersion;
import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.paging;

/** Projects and their assignments (platform admins). */
@RestController
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
class ProjectController {

    record ProjectRequest(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 255) String name,
                          @NotNull String status, BigDecimal budget, LocalDate startDate, LocalDate endDate,
                          Long departmentId) {
        ProjectService.ProjectInput input() {
            return new ProjectService.ProjectInput(code, name, status, budget, startDate, endDate, departmentId);
        }
    }

    record AssignmentRequest(@NotNull Long personnelId, @Size(max = 255) String role, Integer allocationPercent,
                             LocalDate assignedOn) {
        ProjectService.AssignmentInput input() {
            return new ProjectService.AssignmentInput(personnelId, role, allocationPercent, assignedOn);
        }
    }

    record Ref(long id, String name) {
    }

    record ProjectView(long id, long version, String code, String name, String status, BigDecimal budget,
                       LocalDate startDate, LocalDate endDate, Ref department, long assignments, long allocation) {
    }

    record AssignmentView(long id, long version, long projectId, Ref personnel, String role, Integer allocationPercent,
                          LocalDate assignedOn) {
    }

    private final ProjectService projects;

    ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    /**
     * Filters: {@code q} (code, name), {@code status} (repeatable), {@code departmentId}, {@code budgetMin}/
     * {@code budgetMax}; sort: code, name, status, budget, startDate, endDate (default {@code code}).
     */
    @GetMapping("/api/projects")
    ApiResponse<List<ProjectView>> list(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) List<String> status,
                                        @RequestParam(required = false) Long departmentId,
                                        @RequestParam(required = false) BigDecimal budgetMin,
                                        @RequestParam(required = false) BigDecimal budgetMax,
                                        @RequestParam(required = false) String sort,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer size) {
        int[] paging = paging(page, size);
        return ApiResponse.page(projects.list(new ProjectService.Filter(q, status == null ? List.of() : status,
                departmentId, budgetMin, budgetMax, sort), paging[0], paging[1]), ProjectController::view);
    }

    @GetMapping("/api/projects/statuses")
    ApiResponse<List<String>> statuses() {
        return ApiResponse.of(ProjectService.STATUSES);
    }

    @GetMapping("/api/projects/{id}")
    ResponseEntity<ApiResponse<ProjectView>> get(@PathVariable long id) {
        ProjectService.Listed listed = projects.get(id);
        return ResponseEntity.ok().eTag(etag(listed.project().getVersion())).body(ApiResponse.of(view(listed)));
    }

    @PostMapping("/api/projects")
    ResponseEntity<ApiResponse<ProjectView>> create(@Valid @RequestBody ProjectRequest request) {
        Project project = projects.create(request.input());
        return ResponseEntity.created(URI.create("/api/projects/" + project.getId())).eTag(etag(project.getVersion()))
                .body(ApiResponse.of(view(projects.get(project.getId()))));
    }

    @PutMapping("/api/projects/{id}")
    ResponseEntity<ApiResponse<ProjectView>> update(@PathVariable long id,
                                                    @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                    @Valid @RequestBody ProjectRequest request) {
        Project project = projects.update(id, expectedVersion(ifMatch), request.input());
        return ResponseEntity.ok().eTag(etag(project.getVersion())).body(ApiResponse.of(view(projects.get(id))));
    }

    @DeleteMapping("/api/projects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        projects.delete(id);
    }

    /** The project's people; sort: role, allocationPercent, assignedOn (default {@code -allocationPercent}). */
    @GetMapping("/api/projects/{id}/assignments")
    ApiResponse<List<AssignmentView>> assignments(@PathVariable long id, @RequestParam(required = false) String sort,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size) {
        int[] paging = paging(page, size);
        return ApiResponse.page(projects.assignments(id, sort, paging[0], paging[1]),
                listed -> view(listed.assignment(), listed.personnelName()));
    }

    @PostMapping("/api/projects/{id}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<AssignmentView> assign(@PathVariable long id, @Valid @RequestBody AssignmentRequest request) {
        return ApiResponse.of(view(projects.assign(id, request.input()), null));
    }

    @PutMapping("/api/project-assignments/{id}")
    ResponseEntity<ApiResponse<AssignmentView>> updateAssignment(@PathVariable long id,
                                                                 @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                                 @Valid @RequestBody AssignmentRequest request) {
        ProjectAssignment assignment = projects.updateAssignment(id, expectedVersion(ifMatch), request.input());
        return ResponseEntity.ok().eTag(etag(assignment.getVersion())).body(ApiResponse.of(view(assignment, null)));
    }

    @DeleteMapping("/api/project-assignments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unassign(@PathVariable long id) {
        projects.unassign(id);
    }

    private static ProjectView view(ProjectService.Listed listed) {
        Project project = listed.project();
        return new ProjectView(project.getId(), project.getVersion(), project.getCode(), project.getName(),
                project.getStatus(), project.getBudget(), project.getStartDate(), project.getEndDate(),
                project.getDepartmentId() == null ? null : new Ref(project.getDepartmentId(), listed.departmentName()),
                listed.assignments(), listed.allocation());
    }

    private static AssignmentView view(ProjectAssignment assignment, String personnelName) {
        return new AssignmentView(assignment.getId(), assignment.getVersion(), assignment.getProjectId(),
                new Ref(assignment.getPersonnelId(), personnelName), assignment.getRole(),
                assignment.getAllocationPercent(), assignment.getAssignedOn());
    }
}

@RestControllerAdvice(basePackageClasses = ProjectController.class)
class ProjectExceptionHandler extends ApiExceptionHandlerSupport {
}
