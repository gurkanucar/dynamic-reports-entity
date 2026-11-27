package com.gucardev.dynamicreportsentity.training;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

interface TrainingRecordRepository extends JpaRepository<TrainingRecord, Long> {

    @Query("""
            select r from TrainingRecord r
             where (:personnelId is null or r.personnelId = :personnelId)
               and (:trainingId is null or r.trainingId = :trainingId)
               and (:anyStatus = true or r.status in :statuses)
               and (:renewalFrom is null or r.nextRenewalDate >= :renewalFrom)
               and (:renewalTo is null or r.nextRenewalDate <= :renewalTo)
               and (:completedFrom is null or r.completedOn >= :completedFrom)
               and (:completedTo is null or r.completedOn <= :completedTo)""")
    Page<TrainingRecord> search(@Param("personnelId") Long personnelId, @Param("trainingId") Long trainingId,
                                @Param("anyStatus") boolean anyStatus, @Param("statuses") Collection<String> statuses,
                                @Param("renewalFrom") LocalDate renewalFrom, @Param("renewalTo") LocalDate renewalTo,
                                @Param("completedFrom") LocalDate completedFrom,
                                @Param("completedTo") LocalDate completedTo, Pageable pageable);

    /** {trainingId, records, overdue} per training. */
    @Query("""
            select r.trainingId, count(r), sum(case when r.nextRenewalDate < :today then 1 else 0 end)
              from TrainingRecord r where r.trainingId in :ids group by r.trainingId""")
    List<Object[]> summarize(@Param("ids") Collection<Long> ids, @Param("today") LocalDate today);

    long countByTrainingId(Long trainingId);

    @Modifying
    @Query("delete from TrainingRecord r where r.personnelId = :id")
    int deleteByPersonnel(@Param("id") long id);
}
