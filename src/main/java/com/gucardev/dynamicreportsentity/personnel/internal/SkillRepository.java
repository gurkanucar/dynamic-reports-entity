package com.gucardev.dynamicreportsentity.personnel.internal;

import com.gucardev.dynamicreportsentity.personnel.Skill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    @Query("""
            select s from Skill s
             where (:q is null or lower(s.name) like :q escape '\\')
               and (:category is null or s.category = :category)""")
    Page<Skill> search(@Param("q") String q, @Param("category") String category, Pageable pageable);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsByNameIgnoreCase(String name);

    @Query("select distinct s.category from Skill s where s.category is not null order by s.category")
    List<String> categories();

    List<Skill> findAllByOrderByName();
}
