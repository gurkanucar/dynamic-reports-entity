package com.gucardev.dynamicreportsentity.organization.web;

import com.gucardev.dynamicreportsentity.organization.Department;
import com.gucardev.dynamicreportsentity.organization.DepartmentService;
import com.gucardev.dynamicreportsentity.platform.api.ApiExceptionHandlerSupport;
import com.gucardev.dynamicreportsentity.platform.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

import java.net.URI;
import java.util.List;
import java.util.Map;

import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.etag;
import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.expectedVersion;
import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.paging;

/** Departments (platform admins). */
@RestController
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
class DepartmentController {

    record DepartmentRequest(@NotBlank @Size(max = 255) String name, @Size(max = 255) String costCenter, Long parentId,
                             Long managerId) {
        DepartmentService.DepartmentInput input() {
            return new DepartmentService.DepartmentInput(name, costCenter, parentId, managerId);
        }
    }

    record Ref(long id, String name) {
    }

    record DepartmentView(long id, long version, String name, String costCenter, Ref parent, Ref manager,
                          long subDepartments, Map<String, Long> usage) {
    }

    private final DepartmentService departments;

    DepartmentController(DepartmentService departments) {
        this.departments = departments;
    }

    /** Filters: {@code q} (name, cost center), {@code parentId}, {@code roots=true}; sort: name, costCenter. */
    @GetMapping("/api/departments")
    ApiResponse<List<DepartmentView>> list(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) Long parentId,
                                           @RequestParam(defaultValue = "false") boolean roots,
                                           @RequestParam(required = false) String sort,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size) {
        int[] paging = paging(page, size);
        return ApiResponse.page(departments.list(q, parentId, roots, sort, paging[0], paging[1]), DepartmentController::view);
    }

    /** Every department, id and name only: for pickers. */
    @GetMapping("/api/departments/lookup")
    @PreAuthorize("hasAuthority('PLATFORM_USER')")
    ApiResponse<List<Ref>> lookup() {
        return ApiResponse.of(departments.all().stream().map(d -> new Ref(d.getId(), d.getName())).toList());
    }

    @GetMapping("/api/departments/{id}")
    ResponseEntity<ApiResponse<DepartmentView>> get(@PathVariable long id) {
        DepartmentService.Listed listed = departments.get(id);
        return ResponseEntity.ok().eTag(etag(listed.department().getVersion())).body(ApiResponse.of(view(listed)));
    }

    @PostMapping("/api/departments")
    ResponseEntity<ApiResponse<DepartmentView>> create(@Valid @RequestBody DepartmentRequest request) {
        Department department = departments.create(request.input());
        return ResponseEntity.created(URI.create("/api/departments/" + department.getId()))
                .eTag(etag(department.getVersion())).body(ApiResponse.of(view(departments.get(department.getId()))));
    }

    @PutMapping("/api/departments/{id}")
    ResponseEntity<ApiResponse<DepartmentView>> update(@PathVariable long id,
                                                       @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                       @Valid @RequestBody DepartmentRequest request) {
        Department department = departments.update(id, expectedVersion(ifMatch), request.input());
        return ResponseEntity.ok().eTag(etag(department.getVersion())).body(ApiResponse.of(view(departments.get(id))));
    }

    @DeleteMapping("/api/departments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        departments.delete(id);
    }

    private static DepartmentView view(DepartmentService.Listed listed) {
        Department department = listed.department();
        return new DepartmentView(department.getId(), department.getVersion(), department.getName(),
                department.getCostCenter(),
                department.getParentId() == null ? null : new Ref(department.getParentId(), listed.parentName()),
                department.getManagerId() == null ? null : new Ref(department.getManagerId(), listed.managerName()),
                listed.children(), listed.usage());
    }
}

@RestControllerAdvice(basePackageClasses = DepartmentController.class)
class OrganizationExceptionHandler extends ApiExceptionHandlerSupport {
}
