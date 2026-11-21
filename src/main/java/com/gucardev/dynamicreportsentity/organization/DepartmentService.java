package com.gucardev.dynamicreportsentity.organization;

import com.gucardev.dynamicreportsentity.platform.api.Admin;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.api.ListQuery;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Departments: the hierarchy the rest of the company hangs on. */
@Service
public class DepartmentService {

    /** What an admin enters. */
    public record DepartmentInput(String name, String costCenter, Long parentId, Long managerId) {
    }

    /** A department with what the list shows beside it. */
    public record Listed(Department department, String parentName, String managerName, long children,
                         Map<String, Long> usage) {
    }

    public static final Map<String, String> SORTS = Map.of("name", "name", "costCenter", "costCenter", "id", "id");

    private final DepartmentRepository departments;
    // looked up when used: the modules implementing them depend on this service themselves
    private final ObjectProvider<DepartmentUsage> usages;
    private final ObjectProvider<PersonDirectory> people;

    DepartmentService(DepartmentRepository departments, ObjectProvider<DepartmentUsage> usages,
                      ObjectProvider<PersonDirectory> people) {
        this.departments = departments;
        this.usages = usages;
        this.people = people;
    }

    @Transactional(readOnly = true)
    public Page<Listed> list(String q, Long parentId, boolean roots, String sort, int page, int size) {
        Page<Department> found = departments.search(ListQuery.like(q), parentId, roots,
                ListQuery.page(page, size, ListQuery.sort(sort, SORTS, "name")));
        return decorate(found);
    }

    /** Every department by name: the pickers of the other screens (a company has at most a few hundred). */
    @Transactional(readOnly = true)
    public List<Department> all() {
        return departments.findAllByOrderByName();
    }

    public boolean exists(long id) {
        return departments.existsById(id);
    }

    /** Names by id, for other modules' lists. */
    @Transactional(readOnly = true)
    public Map<Long, String> names(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            departments.findAllById(ids).forEach(department -> names.put(department.getId(), department.getName()));
        }
        return names;
    }

    public Optional<String> name(long id) {
        return departments.findById(id).map(Department::getName);
    }

    @Transactional(readOnly = true)
    public Listed get(long id) {
        Department department = departments.findById(id).orElseThrow(Admin.notFound("DEPARTMENT_NOT_FOUND", "department", id));
        return decorate(new org.springframework.data.domain.PageImpl<>(List.of(department))).getContent().getFirst();
    }

    @Transactional
    public Department create(DepartmentInput input) {
        Department department = new Department(input.name());
        validate(null, input);
        department.define(input.name().strip(), Admin.text(input.costCenter()), input.parentId(), input.managerId());
        return departments.saveAndFlush(department);
    }

    @Transactional
    public Department update(long id, long expectedVersion, DepartmentInput input) {
        Department department = departments.findById(id).orElseThrow(Admin.notFound("DEPARTMENT_NOT_FOUND", "department", id));
        Admin.requireVersion(department.getVersion(), expectedVersion);
        validate(id, input);
        department.define(input.name().strip(), Admin.text(input.costCenter()), input.parentId(), input.managerId());
        try {
            return departments.saveAndFlush(department);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw Admin.versionMismatch();
        }
    }

    /** Only a department nobody uses: no sub-departments, members or projects. */
    @Transactional
    public void delete(long id) {
        Department department = departments.findById(id).orElseThrow(Admin.notFound("DEPARTMENT_NOT_FOUND", "department", id));
        List<String> users = new ArrayList<>();
        long children = departments.countByParentId(id);
        if (children > 0) {
            users.add(children + " alt departman");
        }
        for (DepartmentUsage usage : usages.orderedStream().toList()) {
            long count = usage.countByDepartment(List.of(id)).getOrDefault(id, 0L);
            if (count > 0) {
                users.add(count + " " + usage.label());
            }
        }
        if (!users.isEmpty()) {
            throw Admin.inUse("DEPARTMENT_IN_USE", "The department", users);
        }
        departments.delete(department);
    }

    private void validate(Long id, DepartmentInput input) {
        List<FieldProblem> problems = new ArrayList<>();
        Admin.required(problems, "name", input.name());
        Admin.maxLength(problems, "name", input.name(), 255);
        Admin.maxLength(problems, "costCenter", input.costCenter(), 255);
        if (input.parentId() != null) {
            if (!departments.existsById(input.parentId())) {
                problems.add(new FieldProblem("parentId", "DEPARTMENT_NOT_FOUND", "The parent department does not exist."));
            } else if (id != null && createsCycle(id, input.parentId())) {
                problems.add(new FieldProblem("parentId", "HIERARCHY_CYCLE",
                        "A department cannot be under itself or one of its sub-departments."));
            }
        }
        if (input.managerId() != null && personNames(List.of(input.managerId())).isEmpty()) {
            problems.add(new FieldProblem("managerId", "PERSONNEL_NOT_FOUND", "The manager does not exist."));
        }
        Admin.check(problems);
    }

    /** Walks up from the new parent; meeting the department itself means a loop. */
    private boolean createsCycle(long id, long parentId) {
        Set<Long> seen = new HashSet<>();
        Long current = parentId;
        while (current != null && seen.add(current)) {
            if (current == id) {
                return true;
            }
            current = departments.findById(current).map(Department::getParentId).orElse(null);
        }
        return false;
    }

    private Page<Listed> decorate(Page<Department> found) {
        List<Long> ids = found.getContent().stream().map(Department::getId).toList();
        Set<Long> parentIds = new HashSet<>();
        Set<Long> managerIds = new HashSet<>();
        found.getContent().forEach(department -> {
            if (department.getParentId() != null) {
                parentIds.add(department.getParentId());
            }
            if (department.getManagerId() != null) {
                managerIds.add(department.getManagerId());
            }
        });
        Map<Long, String> parents = new HashMap<>();
        departments.findAllById(parentIds).forEach(parent -> parents.put(parent.getId(), parent.getName()));
        Map<Long, String> managers = personNames(managerIds);
        Map<Long, Long> children = new HashMap<>();
        Map<String, Map<Long, Long>> usage = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            departments.countChildren(ids).forEach(row -> children.put((Long) row[0], (Long) row[1]));
            usages.orderedStream().forEach(item -> usage.put(item.label(), item.countByDepartment(ids)));
        }
        return found.map(department -> {
            Map<String, Long> counts = new LinkedHashMap<>();
            usage.forEach((label, byId) -> counts.put(label, byId.getOrDefault(department.getId(), 0L)));
            return new Listed(department, parents.get(department.getParentId()), managers.get(department.getManagerId()),
                    children.getOrDefault(department.getId(), 0L), counts);
        });
    }

    private Map<Long, String> personNames(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            people.orderedStream().forEach(directory -> names.putAll(directory.names(ids)));
        }
        return names;
    }
}
