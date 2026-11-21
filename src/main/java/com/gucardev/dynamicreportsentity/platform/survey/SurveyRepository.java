package com.gucardev.dynamicreportsentity.platform.survey;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

interface SurveyRepository extends JpaRepository<Survey, Long> {

    Page<Survey> findByOwner(String owner, Pageable pageable);

    /** Surveys taking responses now. */
    @Query("select s from Survey s where s.status = com.gucardev.dynamicreportsentity.platform.survey.Survey.Status.PUBLISHED"
            + " and (s.closesAt is null or s.closesAt > :now)")
    Page<Survey> findOpen(@Param("now") Instant now, Pageable pageable);
}
