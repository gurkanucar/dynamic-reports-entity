package com.gucardev.dynamicreportsentity.personnel.internal;

import com.gucardev.dynamicreportsentity.personnel.PersonnelSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PersonnelSkillRepository extends JpaRepository<PersonnelSkill, PersonnelSkill.Key> {

    List<PersonnelSkill> findByPersonnelId(Long personnelId);

    @Modifying
    @Query("delete from PersonnelSkill s where s.personnelId = :id")
    int deleteByPersonnel(@Param("id") long id);

    @Query("select s.skillId, count(s) from PersonnelSkill s where s.skillId in :ids group by s.skillId")
    List<Object[]> countHolders(@Param("ids") Collection<Long> ids);

    @Query("select s.personnelId, count(s) from PersonnelSkill s where s.personnelId in :ids group by s.personnelId")
    List<Object[]> countSkills(@Param("ids") Collection<Long> ids);
}
