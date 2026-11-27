package com.gucardev.dynamicreportsentity.platform.assistant;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Every state change is a conditional update: it only applies from the expected state (0 rows = someone else won). */
interface DatasetSuggestionRepository extends JpaRepository<DatasetSuggestion, UUID> {

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DatasetSuggestion s set s.status = com.gucardev.dynamicreportsentity.platform.assistant.DatasetSuggestion.Status.PROCESSING,
                   s.startedAt = :now
             where s.id = :id and s.status = com.gucardev.dynamicreportsentity.platform.assistant.DatasetSuggestion.Status.QUEUED""")
    int start(@Param("id") UUID id, @Param("now") Instant now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DatasetSuggestion s set s.status = com.gucardev.dynamicreportsentity.platform.assistant.DatasetSuggestion.Status.COMPLETED,
                   s.resultJson = :result, s.model = :model, s.finishedAt = :now
             where s.id = :id and s.status = com.gucardev.dynamicreportsentity.platform.assistant.DatasetSuggestion.Status.PROCESSING""")
    int complete(@Param("id") UUID id, @Param("result") String result, @Param("model") String model,
                 @Param("now") Instant now);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DatasetSuggestion s set s.status = com.gucardev.dynamicreportsentity.platform.assistant.DatasetSuggestion.Status.FAILED,
                   s.errorCode = :code, s.errorMessage = :message, s.finishedAt = :now
             where s.id = :id and s.status in (
                   com.gucardev.dynamicreportsentity.platform.assistant.DatasetSuggestion.Status.QUEUED,
                   com.gucardev.dynamicreportsentity.platform.assistant.DatasetSuggestion.Status.PROCESSING)""")
    int fail(@Param("id") UUID id, @Param("code") String code, @Param("message") String message,
             @Param("now") Instant now);

    /** Requests whose instance died or that ran too long. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DatasetSuggestion s set s.status = com.gucardev.dynamicreportsentity.platform.assistant.DatasetSuggestion.Status.FAILED,
                   s.errorCode = 'SUGGESTION_TIMED_OUT', s.errorMessage = 'The assistant did not answer in time.',
                   s.finishedAt = :now
             where s.createdAt < :cutoff and s.status in (
                   com.gucardev.dynamicreportsentity.platform.assistant.DatasetSuggestion.Status.QUEUED,
                   com.gucardev.dynamicreportsentity.platform.assistant.DatasetSuggestion.Status.PROCESSING)""")
    int expire(@Param("cutoff") Instant cutoff, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("delete from DatasetSuggestion s where s.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
