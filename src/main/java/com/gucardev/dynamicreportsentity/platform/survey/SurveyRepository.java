package com.gucardev.dynamicreportsentity.platform.survey;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;

interface SurveyRepository extends JpaRepository<Survey, UUID> {

    /** Surveys of one owner ({@code owner} null = all) in some statuses, {@code q}: a LIKE pattern over title. */
    @Query("""
            select s from Survey s
             where (:owner is null or s.owner = :owner)
               and s.status in :statuses
               and (:q is null or lower(s.title) like :q escape '\\' or lower(s.description) like :q escape '\\')""")
    Page<Survey> search(@Param("owner") String owner, @Param("statuses") Collection<Survey.Status> statuses,
                        @Param("q") String q, Pageable pageable);

    /** Surveys taking responses now. */
    @Query("""
            select s from Survey s
             where s.status = com.gucardev.dynamicreportsentity.platform.survey.Survey.Status.PUBLISHED
               and (s.closesAt is null or s.closesAt > :now)
               and (:q is null or lower(s.title) like :q escape '\\')""")
    Page<Survey> findOpen(@Param("now") Instant now, @Param("q") String q, Pageable pageable);
}
