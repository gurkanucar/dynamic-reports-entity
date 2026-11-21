package com.gucardev.dynamicreportsentity.platform.survey;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

interface SurveyResponseRepository extends JpaRepository<SurveyResponse, UUID> {

    Page<SurveyResponse> findBySurveyId(UUID surveyId, Pageable pageable);

    /** The owner's response list: follow-up filters, a LIKE pattern over respondent and answers, a date range. */
    @Query("""
            select r from SurveyResponse r
             where r.surveyId = :surveyId
               and r.followStatus in :statuses
               and (:anyPriority = true or r.priority in :priorities)
               and (:assignee is null or r.assignee = :assignee)
               and (:q is null or lower(r.respondent) like :q escape '\\' or lower(r.answersJson) like :q escape '\\'
                    or lower(r.assignee) like :q escape '\\')
               and (:from is null or r.submittedAt >= :from)
               and (:to is null or r.submittedAt < :to)""")
    Page<SurveyResponse> search(@Param("surveyId") UUID surveyId,
                                @Param("statuses") Collection<SurveyResponse.FollowStatus> statuses,
                                @Param("anyPriority") boolean anyPriority,
                                @Param("priorities") Collection<SurveyResponse.Priority> priorities,
                                @Param("assignee") String assignee, @Param("q") String q,
                                @Param("from") java.time.Instant from, @Param("to") java.time.Instant to,
                                Pageable pageable);

    @Query("select r.followStatus, count(r) from SurveyResponse r where r.surveyId = :surveyId group by r.followStatus")
    List<Object[]> countByStatus(@Param("surveyId") UUID surveyId);

    Stream<SurveyResponse> streamBySurveyIdOrderById(UUID surveyId);

    long countBySurveyId(UUID surveyId);

    @Query("select r.surveyId, count(r) from SurveyResponse r where r.surveyId in :ids group by r.surveyId")
    List<Object[]> countBySurveyIds(@Param("ids") Collection<UUID> ids);

    /** The surveys among {@code ids} this user has answered (named or with their respondent key). */
    @Query("select distinct r.surveyId from SurveyResponse r where r.surveyId in :ids"
            + " and (r.respondent = :username or r.respondentKey in :keys)")
    List<UUID> answeredAmong(@Param("ids") Collection<UUID> ids, @Param("username") String username,
                             @Param("keys") Collection<String> keys);

    @Modifying
    @Query("delete from SurveyResponse r where r.surveyId = :surveyId")
    int deleteBySurveyId(@Param("surveyId") UUID surveyId);
}
