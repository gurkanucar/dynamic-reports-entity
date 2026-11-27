package com.gucardev.dynamicreportsentity.personnel;

import com.gucardev.entityreports.DatasetEntity;
import com.gucardev.entityreports.DatasetReference;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Pure many-to-many junction (no extra columns, composite key): Personnel ⇄ Skill.
 * Paths: {@code Personnel: skills.skill.name}, {@code Skill: holders.personnel.fullName}.
 */
@Entity
@Table(name = "personnel_skill", indexes = @Index(name = "ix_personnel_skill_skill_id", columnList = "skill_id"))
@IdClass(PersonnelSkill.Key.class)
@DatasetEntity(label = "Personnel Skill")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonnelSkill {

    @Id
    @DatasetReference(target = "Personnel", owner = true, inverse = "skills", inverseLabel = "Skills")
    private Long personnelId;

    @Id
    @DatasetReference(target = "Skill", inverse = "holders", inverseLabel = "Holders")
    private Long skillId;

    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long personnelId;
        private Long skillId;
    }

    public PersonnelSkill(Long personnelId, Long skillId) {
        this.personnelId = personnelId;
        this.skillId = skillId;
    }
}
