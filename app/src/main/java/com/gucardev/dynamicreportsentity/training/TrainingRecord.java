package com.gucardev.dynamicreportsentity.training;

import com.gucardev.entityreports.DatasetEntity;
import com.gucardev.entityreports.DatasetField;
import com.gucardev.entityreports.DatasetHidden;
import com.gucardev.entityreports.DatasetReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Many-to-many WITH extra columns: Personnel ⇄ Training, carrying completion data.
 * Paths: {@code Personnel: trainingRecords.training.name}, {@code Training: records.personnel.fullName}.
 * <p>
 * Reference columns are JOIN keys for every dataset: index them. Without a real FK, databases
 * like PostgreSQL never create one automatically.
 */
@Entity
@Table(name = "training_record", indexes = {
        @Index(name = "ix_training_record_personnel_id", columnList = "personnel_id"),
        @Index(name = "ix_training_record_training_id", columnList = "training_id"),
        @Index(name = "ix_training_record_next_renewal_date", columnList = "next_renewal_date")
})
@DatasetEntity(label = "Training Record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Version
    @DatasetHidden
    private long version;

    /*
     * Classic approach:  @ManyToOne Personnel personnel;   (couples this module to personnel)
     * Our approach:      just the ID + "this ID points at Personnel" for the dataset engine.
     */
    @DatasetReference(target = "Personnel", owner = true, inverse = "trainingRecords", inverseLabel = "Training Records")
    @Column(nullable = false)
    private Long personnelId;

    @DatasetReference(target = "Training", inverse = "records", inverseLabel = "Records")
    @Column(nullable = false)
    private Long trainingId;

    @DatasetField(label = "Completed On")
    private LocalDate completedOn;

    @DatasetField(label = "Next Renewal Date")
    private LocalDate nextRenewalDate;

    @DatasetField(label = "Score")
    private Integer score;

    @DatasetField(label = "Status")
    private String status;

    TrainingRecord(Long personnelId, Long trainingId) {
        this.personnelId = personnelId;
        this.trainingId = trainingId;
    }

    void define(Long personnelId, Long trainingId, LocalDate completedOn, LocalDate nextRenewalDate, Integer score,
                String status) {
        this.personnelId = personnelId;
        this.trainingId = trainingId;
        this.completedOn = completedOn;
        this.nextRenewalDate = nextRenewalDate;
        this.score = score;
        this.status = status;
    }
}
