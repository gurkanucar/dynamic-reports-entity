package com.gucardev.dynamicreportsentity.personnel;

import com.gucardev.dynamicreportsentity.personnel.internal.PersonnelSkillRepository;
import com.gucardev.dynamicreportsentity.personnel.internal.SkillRepository;
import com.gucardev.dynamicreportsentity.platform.api.Admin;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.api.ListQuery;
import org.springframework.data.domain.Page;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The skill catalog people pick from. */
@Service
public class SkillService {

    public record SkillInput(String name, String category) {
    }

    public record Listed(Skill skill, long holders) {
    }

    private static final Map<String, String> SORTS = Map.of("name", "name", "category", "category", "id", "id");

    private final SkillRepository skills;
    private final PersonnelSkillRepository holders;

    SkillService(SkillRepository skills, PersonnelSkillRepository holders) {
        this.skills = skills;
        this.holders = holders;
    }

    @Transactional(readOnly = true)
    public Page<Listed> list(String q, String category, String sort, int page, int size) {
        Page<Skill> found = skills.search(ListQuery.like(q), Admin.text(category),
                ListQuery.page(page, size, ListQuery.sort(sort, SORTS, "name")));
        Map<Long, Long> counts = new HashMap<>();
        List<Long> ids = found.getContent().stream().map(Skill::getId).toList();
        if (!ids.isEmpty()) {
            holders.countHolders(ids).forEach(row -> counts.put((Long) row[0], (Long) row[1]));
        }
        return found.map(skill -> new Listed(skill, counts.getOrDefault(skill.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public List<Skill> all() {
        return skills.findAllByOrderByName();
    }

    public List<String> categories() {
        return skills.categories();
    }

    @Transactional
    public Skill create(SkillInput input) {
        validate(null, input);
        Skill skill = new Skill(input.name().strip());
        skill.define(input.name().strip(), Admin.text(input.category()));
        return skills.saveAndFlush(skill);
    }

    @Transactional
    public Skill update(long id, long expectedVersion, SkillInput input) {
        Skill skill = find(id);
        Admin.requireVersion(skill.getVersion(), expectedVersion);
        validate(id, input);
        skill.define(input.name().strip(), Admin.text(input.category()));
        try {
            return skills.saveAndFlush(skill);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw Admin.versionMismatch();
        }
    }

    /** Only a skill nobody has. */
    @Transactional
    public void delete(long id) {
        Skill skill = find(id);
        long count = holders.countHolders(List.of(id)).stream().mapToLong(row -> (Long) row[1]).sum();
        if (count > 0) {
            throw Admin.inUse("SKILL_IN_USE", "The skill", List.of(count + " kişi"));
        }
        skills.delete(skill);
    }

    private Skill find(long id) {
        return skills.findById(id).orElseThrow(Admin.notFound("SKILL_NOT_FOUND", "skill", id));
    }

    private void validate(Long id, SkillInput input) {
        List<FieldProblem> problems = new ArrayList<>();
        Admin.required(problems, "name", input.name());
        Admin.maxLength(problems, "name", input.name(), 255);
        Admin.maxLength(problems, "category", input.category(), 255);
        if (input.name() != null && !input.name().isBlank() && (id == null
                ? skills.existsByNameIgnoreCase(input.name().strip())
                : skills.existsByNameIgnoreCaseAndIdNot(input.name().strip(), id))) {
            problems.add(new FieldProblem("name", "NAME_TAKEN", "A skill with this name exists."));
        }
        Admin.check(problems);
    }
}
