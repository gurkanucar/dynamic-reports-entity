package com.gucardev.dynamicreportsentity.personnel.web;

import com.gucardev.dynamicreportsentity.personnel.Personnel;
import com.gucardev.dynamicreportsentity.personnel.PersonnelService;
import com.gucardev.dynamicreportsentity.personnel.Skill;
import com.gucardev.dynamicreportsentity.personnel.SkillService;
import com.gucardev.dynamicreportsentity.platform.api.ApiExceptionHandlerSupport;
import com.gucardev.dynamicreportsentity.platform.api.ApiResponse;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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

/** People and skills (platform admins). Salary and national id only reach users allowed to see them. */
@RestController
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
class PersonnelController {

    record ProfileBody(LocalDate birthDate, @Size(max = 10) String bloodType, @Size(max = 255) String city,
                       @Size(max = 255) String emergencyContact) {
    }

    record PersonnelRequest(@Size(max = 100) String username, @Size(max = 255) String fullName,
                            @Size(max = 255) String email, LocalDate hireDate, Boolean active, Long departmentId,
                            Long managerId, BigDecimal salary, @Size(max = 20) String nationalId, ProfileBody profile) {
        PersonnelService.PersonnelInput input() {
            return new PersonnelService.PersonnelInput(username, fullName, email, hireDate, !Boolean.FALSE.equals(active),
                    departmentId, managerId, salary, nationalId, profile == null ? null
                    : new PersonnelService.Profile(profile.birthDate(), profile.bloodType(), profile.city(),
                    profile.emergencyContact()));
        }
    }

    record SkillsRequest(@NotNull List<Long> skillIds) {
    }

    record SkillRequest(@NotBlank @Size(max = 255) String name, @Size(max = 255) String category) {
    }

    record Ref(long id, String name) {
    }

    /**
     * @param salary     null unless the caller holds SALARY_VIEW
     * @param nationalId null unless the caller holds NATIONAL_ID_VIEW
     */
    record PersonnelView(long id, long version, String username, String fullName, String email, LocalDate hireDate,
                         boolean active, Ref department, Ref manager, long directReports, long skillCount,
                         BigDecimal salary, String nationalId) {
    }

    record PersonnelDetailView(PersonnelView personnel, PersonnelService.Profile profile, List<SkillRef> skills,
                               boolean canSeeSalary, boolean canSeeNationalId) {
    }

    record SkillRef(long id, String name, String category) {
    }

    record SkillView(long id, long version, String name, String category, long holders) {
    }

    private final PersonnelService personnel;
    private final SkillService skills;

    PersonnelController(PersonnelService personnel, SkillService skills) {
        this.personnel = personnel;
        this.skills = skills;
    }

    // ---- people ----------------------------------------------------------------------------------------

    /**
     * Filters: {@code q} (name, e-mail, username), {@code departmentId} (repeatable), {@code active},
     * {@code managerId}, {@code hiredFrom}/{@code hiredTo}; sort: fullName, email, hireDate, active, salary
     * (with SALARY_VIEW). Default {@code fullName}.
     */
    @GetMapping("/api/personnel")
    ApiResponse<List<PersonnelView>> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) List<Long> departmentId,
                                          @RequestParam(required = false) Boolean active,
                                          @RequestParam(required = false) Long managerId,
                                          @RequestParam(required = false) LocalDate hiredFrom,
                                          @RequestParam(required = false) LocalDate hiredTo,
                                          @RequestParam(required = false) String sort,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size,
                                          Authentication authentication) {
        Actor viewer = Actor.of(authentication);
        int[] paging = paging(page, size);
        PersonnelService.Filter filter = new PersonnelService.Filter(q, departmentId == null ? List.of() : departmentId,
                active, managerId, hiredFrom, hiredTo, sort);
        return ApiResponse.page(personnel.list(filter, viewer, paging[0], paging[1]), listed -> view(listed, viewer));
    }

    /** At most 50 people (id, name) for pickers: by {@code q}, or the given {@code id}s. */
    @GetMapping("/api/personnel/lookup")
    @PreAuthorize("hasAuthority('PLATFORM_USER')")
    ApiResponse<List<Ref>> lookup(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) List<Long> id) {
        return ApiResponse.of(personnel.lookup(q, id == null ? List.of() : id).stream()
                .map(person -> new Ref(person.getId(), person.getFullName())).toList());
    }

    @GetMapping("/api/personnel/{id}")
    ResponseEntity<ApiResponse<PersonnelDetailView>> get(@PathVariable long id, Authentication authentication) {
        Actor viewer = Actor.of(authentication);
        PersonnelService.Detail detail = personnel.get(id);
        return ResponseEntity.ok().eTag(etag(detail.listed().personnel().getVersion()))
                .body(ApiResponse.of(detail(detail, viewer)));
    }

    @PostMapping("/api/personnel")
    ResponseEntity<ApiResponse<PersonnelDetailView>> create(@Valid @RequestBody PersonnelRequest request,
                                                            Authentication authentication) {
        Actor viewer = Actor.of(authentication);
        Personnel person = personnel.create(request.input(), viewer);
        return ResponseEntity.created(URI.create("/api/personnel/" + person.getId())).eTag(etag(person.getVersion()))
                .body(ApiResponse.of(detail(personnel.get(person.getId()), viewer)));
    }

    @PutMapping("/api/personnel/{id}")
    ResponseEntity<ApiResponse<PersonnelDetailView>> update(@PathVariable long id,
                                                            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                            @Valid @RequestBody PersonnelRequest request,
                                                            Authentication authentication) {
        Actor viewer = Actor.of(authentication);
        Personnel person = personnel.update(id, expectedVersion(ifMatch), request.input(), viewer);
        return ResponseEntity.ok().eTag(etag(person.getVersion())).body(ApiResponse.of(detail(personnel.get(id), viewer)));
    }

    @DeleteMapping("/api/personnel/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        personnel.delete(id);
    }

    /** The person's skills become exactly these. */
    @PutMapping("/api/personnel/{id}/skills")
    ApiResponse<List<SkillRef>> setSkills(@PathVariable long id, @Valid @RequestBody SkillsRequest request) {
        return ApiResponse.of(personnel.setSkills(id, request.skillIds()).stream().map(PersonnelController::ref).toList());
    }

    // ---- skills ----------------------------------------------------------------------------------------

    /** Filters: {@code q} (name), {@code category}; sort: name, category. */
    @GetMapping("/api/skills")
    ApiResponse<List<SkillView>> skills(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) String sort,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer size) {
        int[] paging = paging(page, size);
        return ApiResponse.page(skills.list(q, category, sort, paging[0], paging[1]), listed -> new SkillView(
                listed.skill().getId(), listed.skill().getVersion(), listed.skill().getName(),
                listed.skill().getCategory(), listed.holders()));
    }

    /** Every skill (for pickers) and the categories in use. */
    @GetMapping("/api/skills/lookup")
    ApiResponse<List<SkillRef>> skillLookup() {
        return ApiResponse.of(skills.all().stream().map(PersonnelController::ref).toList());
    }

    @GetMapping("/api/skills/categories")
    ApiResponse<List<String>> categories() {
        return ApiResponse.of(skills.categories());
    }

    @PostMapping("/api/skills")
    ResponseEntity<ApiResponse<SkillView>> createSkill(@Valid @RequestBody SkillRequest request) {
        Skill skill = skills.create(new SkillService.SkillInput(request.name(), request.category()));
        return ResponseEntity.created(URI.create("/api/skills/" + skill.getId())).eTag(etag(skill.getVersion()))
                .body(ApiResponse.of(new SkillView(skill.getId(), skill.getVersion(), skill.getName(),
                        skill.getCategory(), 0)));
    }

    @PutMapping("/api/skills/{id}")
    ResponseEntity<ApiResponse<SkillView>> updateSkill(@PathVariable long id,
                                                       @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                       @Valid @RequestBody SkillRequest request) {
        Skill skill = skills.update(id, expectedVersion(ifMatch), new SkillService.SkillInput(request.name(),
                request.category()));
        return ResponseEntity.ok().eTag(etag(skill.getVersion())).body(ApiResponse.of(new SkillView(skill.getId(),
                skill.getVersion(), skill.getName(), skill.getCategory(), 0)));
    }

    @DeleteMapping("/api/skills/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteSkill(@PathVariable long id) {
        skills.delete(id);
    }

    // ---- views -----------------------------------------------------------------------------------------

    private static SkillRef ref(Skill skill) {
        return new SkillRef(skill.getId(), skill.getName(), skill.getCategory());
    }

    private static PersonnelView view(PersonnelService.Listed listed, Actor viewer) {
        Personnel person = listed.personnel();
        return new PersonnelView(person.getId(), person.getVersion(), person.getUsername(), person.getFullName(),
                person.getEmail(), person.getHireDate(), Boolean.TRUE.equals(person.getActive()),
                person.getDepartmentId() == null ? null : new Ref(person.getDepartmentId(), listed.departmentName()),
                person.getManagerId() == null ? null : new Ref(person.getManagerId(), listed.managerName()),
                listed.directReports(), listed.skills(),
                viewer.hasAuthority(Personnel.SALARY_VIEW) ? person.getSalary() : null,
                viewer.hasAuthority(Personnel.NATIONAL_ID_VIEW) ? person.getNationalId() : null);
    }

    private static PersonnelDetailView detail(PersonnelService.Detail detail, Actor viewer) {
        return new PersonnelDetailView(view(detail.listed(), viewer), detail.profile(),
                detail.skills().stream().map(PersonnelController::ref).toList(),
                viewer.hasAuthority(Personnel.SALARY_VIEW), viewer.hasAuthority(Personnel.NATIONAL_ID_VIEW));
    }
}

@RestControllerAdvice(basePackageClasses = PersonnelController.class)
class PersonnelExceptionHandler extends ApiExceptionHandlerSupport {
}
