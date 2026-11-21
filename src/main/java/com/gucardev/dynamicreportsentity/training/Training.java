package com.gucardev.dynamicreportsentity.training;

import com.gucardev.dynamicreportsentity.platform.dataset.DatasetField;
import com.gucardev.dynamicreportsentity.platform.dataset.DatasetHidden;
import com.gucardev.dynamicreportsentity.platform.dataset.DatasetEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Course catalog entry. Example of the opt-out style: {@code includeAllFields = true} exposes every
 * plain field (labels derived from the names: "Name", "Category", "Mandatory"); {@code @DatasetField}
 * overrides a label, {@code @DatasetHidden} keeps a field out.
 */
@Entity
@Table(name = "training")
@DatasetEntity(label = "Training", includeAllFields = true)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Training {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Version
    @DatasetHidden
    private long version;

    @Column(nullable = false)
    private String name;

    private String category;

    /** Months until renewal; empty if the certificate never expires. Label overridden. */
    @DatasetField(label = "Validity (months)")
    private Integer validityMonths;

    private Boolean mandatory;

    /** Internal remarks of the training team: never dataset-ready. */
    @DatasetHidden
    private String internalNotes;

    Training(String name) {
        this.name = name;
    }

    void define(String name, String category, Integer validityMonths, boolean mandatory, String internalNotes) {
        this.name = name;
        this.category = category;
        this.validityMonths = validityMonths;
        this.mandatory = mandatory;
        this.internalNotes = internalNotes;
    }
}
