package com.gucardev.dynamicreportsentity.personnel;

import com.gucardev.dynamicreportsentity.organization.DepartmentService;
import com.gucardev.dynamicreportsentity.organization.DepartmentUsage;
import com.gucardev.dynamicreportsentity.organization.PersonDirectory;
import com.gucardev.dynamicreportsentity.personnel.internal.PersonnelProfileRepository;
import com.gucardev.dynamicreportsentity.personnel.internal.PersonnelRepository;
import com.gucardev.dynamicreportsentity.personnel.internal.PersonnelSkillRepository;
import com.gucardev.dynamicreportsentity.personnel.internal.SkillRepository;
import com.gucardev.dynamicreportsentity.platform.api.Admin;
import com.gucardev.dynamicreportsentity.platform.api.ApiException;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.api.ListQuery;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * People for the admin screens. Salary and national id are read and written only by users holding
 * {@link Personnel#SALARY_VIEW} / {@link Personnel#NATIONAL_ID_VIEW}, like everywhere else on the platform.
 */
@Service
public class PersonnelService implements PersonDirectory, DepartmentUsage {

    public record Profile(LocalDate birthDate, String bloodType, String city, String emergencyContact) {
    }

    public record PersonnelInput(String username, String fullName, String email, LocalDate hireDate, boolean active,
                                 Long departmentId, Long managerId, BigDecimal salary, String nationalId,
                                 Profile profile) {
    }

    public record Filter(String q, List<Long> departments, Boolean active, Long managerId, LocalDate hiredFrom,
                         LocalDate hiredTo, String sort) {
    }

    /** A person with the names the list shows. */
    public record Listed(Personnel personnel, String departmentName, String managerName, long directReports,
                         long skills) {
    }

    public record Detail(Listed listed, Profile profile, List<Skill> skills) {
    }

    private static final Map<String, String> SORTS = Map.of("fullName", "fullName", "email", "email",
            "hireDate", "hireDate", "active", "active", "id", "id");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern NATIONAL_ID = Pattern.compile("\\d{11}");
    private static final Set<String> BLOOD_TYPES = Set.of("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-");

    private final PersonnelRepository people;
    private final PersonnelProfileRepository profiles;
    private final PersonnelSkillRepository personnelSkills;
    private final SkillRepository skills;
    private final DepartmentService departments;
    private final ApplicationEventPublisher events;

    PersonnelService(PersonnelRepository people, PersonnelProfileRepository profiles,
                     PersonnelSkillRepository personnelSkills, SkillRepository skills, DepartmentService departments,
                     ApplicationEventPublisher events) {
        this.people = people;
        this.profiles = profiles;
        this.personnelSkills = personnelSkills;
        this.skills = skills;
        this.departments = departments;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<Listed> list(Filter filter, Actor viewer, int page, int size) {
        ListQuery.range(filter.hiredFrom(), filter.hiredTo(), "hired");
        Map<String, String> sorts = new HashMap<>(SORTS);
        if (viewer.hasAuthority(Personnel.SALARY_VIEW)) {
            sorts.put("salary", "salary");   // sorting by salary would reveal it
        }
        Page<Personnel> found = people.search(ListQuery.like(filter.q()), filter.departments().isEmpty(),
                filter.departments().isEmpty() ? List.of(-1L) : filter.departments(), filter.active(),
                filter.managerId(), filter.hiredFrom(), filter.hiredTo(),
                ListQuery.page(page, size, ListQuery.sort(filter.sort(), sorts, "fullName")));
        return decorate(found);
    }

    /** At most 50 people by name (pickers): {@code q} over name and e-mail, or the given ids. */
    @Transactional(readOnly = true)
    public List<Personnel> lookup(String q, Collection<Long> ids) {
        if (!ids.isEmpty()) {
            return people.findAllById(ids);
        }
        return people.search(ListQuery.like(q), true, List.of(-1L), null, null, null, null,
                PageRequest.of(0, 50, Sort.by("fullName"))).getContent();
    }

    @Transactional(readOnly = true)
    public Detail get(long id) {
        Personnel person = find(id);
        Listed listed = decorate(new org.springframework.data.domain.PageImpl<>(List.of(person))).getContent().getFirst();
        Profile profile = profiles.findByPersonnelId(id).map(item -> new Profile(item.getBirthDate(),
                item.getBloodType(), item.getCity(), item.getEmergencyContact())).orElse(null);
        List<Long> skillIds = personnelSkills.findByPersonnelId(id).stream().map(PersonnelSkill::getSkillId).toList();
        return new Detail(listed, profile, skills.findAllById(skillIds).stream()
                .sorted(java.util.Comparator.comparing(Skill::getName)).toList());
    }

    public boolean exists(long id) {
        return people.existsById(id);
    }

    @Transactional
    public Personnel create(PersonnelInput input, Actor viewer) {
        validate(null, input, viewer);
        Personnel person = new Personnel(input.fullName());
        apply(person, input, viewer);
        try {
            person = people.saveAndFlush(person);
        } catch (DataIntegrityViolationException e) {
            throw usernameTaken();
        }
        saveProfile(person.getId(), input.profile());
        return person;
    }

    @Transactional
    public Personnel update(long id, long expectedVersion, PersonnelInput input, Actor viewer) {
        Personnel person = find(id);
        Admin.requireVersion(person.getVersion(), expectedVersion);
        validate(id, input, viewer);
        apply(person, input, viewer);
        try {
            person = people.saveAndFlush(person);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw Admin.versionMismatch();
        } catch (DataIntegrityViolationException e) {
            throw usernameTaken();
        }
        saveProfile(id, input.profile());
        return person;
    }

    /** The person's skills become exactly these. */
    @Transactional
    public List<Skill> setSkills(long id, Collection<Long> skillIds) {
        find(id);
        Set<Long> wanted = new HashSet<>(skillIds);
        List<Skill> found = skills.findAllById(wanted);
        if (found.size() != wanted.size()) {
            throw ApiException.invalid(List.of(new FieldProblem("skillIds", "SKILL_NOT_FOUND", "Unknown skill id.")));
        }
        personnelSkills.deleteByPersonnel(id);
        personnelSkills.flush();
        wanted.forEach(skillId -> personnelSkills.save(new PersonnelSkill(id, skillId)));
        return found.stream().sorted(java.util.Comparator.comparing(Skill::getName)).toList();
    }

    /**
     * Deletes the person with their profile and skills; direct reports lose their manager. Other modules drop their
     * rows on {@link PersonnelRemoved}. (Usually setting "active" off is what HR wants instead.)
     */
    @Transactional
    public void delete(long id) {
        Personnel person = find(id);
        profiles.findByPersonnelId(id).ifPresent(profiles::delete);
        personnelSkills.deleteByPersonnel(id);
        people.clearManager(id);
        people.delete(person);
        events.publishEvent(new PersonnelRemoved(id, person.getFullName()));
    }

    // ---- PersonDirectory, DepartmentUsage (for the organization module) --------------------------------

    @Override
    public Map<Long, String> names(Collection<Long> personIds) {
        Map<Long, String> names = new HashMap<>();
        people.findAllById(personIds).forEach(person -> names.put(person.getId(), person.getFullName()));
        return names;
    }

    @Override
    public String label() {
        return "çalışan";
    }

    @Override
    public Map<Long, Long> countByDepartment(Collection<Long> departmentIds) {
        Map<Long, Long> counts = new HashMap<>();
        people.countByDepartment(departmentIds).forEach(row -> counts.put((Long) row[0], (Long) row[1]));
        return counts;
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private Personnel find(long id) {
        return people.findById(id).orElseThrow(Admin.notFound("PERSONNEL_NOT_FOUND", "person", id));
    }

    private void apply(Personnel person, PersonnelInput input, Actor viewer) {
        person.define(Admin.text(input.username()), input.fullName().strip(), Admin.text(input.email()),
                input.hireDate(), input.active(), input.departmentId(), input.managerId());
        person.sensitive(input.salary(), Admin.text(input.nationalId()), viewer.hasAuthority(Personnel.SALARY_VIEW),
                viewer.hasAuthority(Personnel.NATIONAL_ID_VIEW));
    }

    private void saveProfile(long personnelId, Profile input) {
        PersonnelProfile profile = profiles.findByPersonnelId(personnelId).orElse(null);
        if (input == null) {
            return;
        }
        if (profile == null) {
            profile = new PersonnelProfile(personnelId);
        }
        profile.define(input.birthDate(), Admin.text(input.bloodType()), Admin.text(input.city()),
                Admin.text(input.emergencyContact()));
        if (profile.isEmpty() && profile.getId() == null) {
            return;
        }
        profiles.save(profile);
    }

    private void validate(Long id, PersonnelInput input, Actor viewer) {
        List<FieldProblem> problems = new ArrayList<>();
        Admin.required(problems, "fullName", input.fullName());
        Admin.maxLength(problems, "fullName", input.fullName(), 255);
        Admin.maxLength(problems, "username", input.username(), 100);
        String email = Admin.text(input.email());
        if (email != null && !EMAIL.matcher(email).matches()) {
            problems.add(new FieldProblem("email", "INVALID_EMAIL", "Not an e-mail address."));
        }
        String username = Admin.text(input.username());
        if (username != null && (id == null ? people.existsByUsername(username) : people.existsByUsernameAndIdNot(username, id))) {
            problems.add(new FieldProblem("username", "USERNAME_TAKEN", "Another person has this username."));
        }
        if (input.departmentId() == null) {
            problems.add(new FieldProblem("departmentId", "REQUIRED", "Choose a department."));
        } else if (!departments.exists(input.departmentId())) {
            problems.add(new FieldProblem("departmentId", "DEPARTMENT_NOT_FOUND", "The department does not exist."));
        }
        if (input.managerId() != null) {
            if (input.managerId().equals(id)) {
                problems.add(new FieldProblem("managerId", "SELF_MANAGER", "A person cannot be their own manager."));
            } else if (!people.existsById(input.managerId())) {
                problems.add(new FieldProblem("managerId", "PERSONNEL_NOT_FOUND", "The manager does not exist."));
            }
        }
        if (input.hireDate() != null && input.hireDate().isAfter(LocalDate.now().plusYears(1))) {
            problems.add(new FieldProblem("hireDate", "OUT_OF_RANGE", "The hire date is more than a year ahead."));
        }
        if (viewer.hasAuthority(Personnel.SALARY_VIEW) && input.salary() != null && input.salary().signum() < 0) {
            problems.add(new FieldProblem("salary", "OUT_OF_RANGE", "The salary cannot be negative."));
        }
        String nationalId = Admin.text(input.nationalId());
        if (viewer.hasAuthority(Personnel.NATIONAL_ID_VIEW) && nationalId != null
                && !NATIONAL_ID.matcher(nationalId).matches()) {
            problems.add(new FieldProblem("nationalId", "INVALID_FORMAT", "A national id is 11 digits."));
        }
        if (input.profile() != null && input.profile().bloodType() != null && !input.profile().bloodType().isBlank()
                && !BLOOD_TYPES.contains(input.profile().bloodType().strip())) {
            problems.add(new FieldProblem("profile.bloodType", "INVALID_VALUE", "One of " + BLOOD_TYPES + "."));
        }
        Admin.check(problems);
    }

    private Page<Listed> decorate(Page<Personnel> found) {
        Set<Long> departmentIds = new HashSet<>();
        Set<Long> managerIds = new HashSet<>();
        List<Long> ids = found.getContent().stream().map(Personnel::getId).toList();
        found.getContent().forEach(person -> {
            if (person.getDepartmentId() != null) {
                departmentIds.add(person.getDepartmentId());
            }
            if (person.getManagerId() != null) {
                managerIds.add(person.getManagerId());
            }
        });
        Map<Long, String> departmentNames = departments.names(departmentIds);
        Map<Long, String> managers = managerIds.isEmpty() ? new HashMap<>() : names(managerIds);
        Map<Long, Long> reports = new LinkedHashMap<>();
        Map<Long, Long> skillCounts = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            people.countReports(ids).forEach(row -> reports.put((Long) row[0], (Long) row[1]));
            personnelSkills.countSkills(ids).forEach(row -> skillCounts.put((Long) row[0], (Long) row[1]));
        }
        return found.map(person -> new Listed(person, departmentNames.get(person.getDepartmentId()),
                managers.get(person.getManagerId()), reports.getOrDefault(person.getId(), 0L),
                skillCounts.getOrDefault(person.getId(), 0L)));
    }

    private static ApiException usernameTaken() {
        return ApiException.invalid(List.of(new FieldProblem("username", "USERNAME_TAKEN",
                "Another person has this username.")));
    }
}
