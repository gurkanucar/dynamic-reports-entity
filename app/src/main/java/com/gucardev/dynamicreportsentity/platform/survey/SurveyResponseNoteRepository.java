package com.gucardev.dynamicreportsentity.platform.survey;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

interface SurveyResponseNoteRepository extends JpaRepository<SurveyResponseNote, UUID> {

    List<SurveyResponseNote> findByResponseIdOrderById(UUID responseId);

    @Query("select n.responseId, count(n) from SurveyResponseNote n where n.responseId in :ids group by n.responseId")
    List<Object[]> countByResponseIds(@Param("ids") Collection<UUID> ids);

    @Modifying
    @Query("delete from SurveyResponseNote n where n.surveyId = :surveyId")
    int deleteBySurveyId(@Param("surveyId") UUID surveyId);
}
