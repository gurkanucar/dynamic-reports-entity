package com.gucardev.dynamicreportsentity.training;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface TrainingRepository extends JpaRepository<Training, Long> {

    @Query("""
            select t from Training t
             where (:q is null or lower(t.name) like :q escape '\\' or lower(t.category) like :q escape '\\')
               and (:category is null or t.category = :category)
               and (:mandatory is null or t.mandatory = :mandatory)""")
    Page<Training> search(@Param("q") String q, @Param("category") String category,
                          @Param("mandatory") Boolean mandatory, Pageable pageable);

    List<Training> findAllByOrderByName();

    @Query("select distinct t.category from Training t where t.category is not null order by t.category")
    List<String> categories();
}
