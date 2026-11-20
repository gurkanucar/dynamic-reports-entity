package com.gucardev.dynamicreportsentity.training;

import com.gucardev.dynamicreportsentity.platform.reporting.ReportField;
import com.gucardev.dynamicreportsentity.platform.reporting.ReportHidden;
import com.gucardev.dynamicreportsentity.platform.reporting.Reportable;
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
 * plain field (labels derived from the names: "Name", "Category", "Mandatory"); {@code @ReportField}
 * overrides a label, {@code @ReportHidden} keeps a field out.
 */
@Entity
@Table(name = "training")
@Reportable(label = "Training", includeAllFields = true)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Training {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String category;

    /** Months until renewal; empty if the certificate never expires. Label overridden. */
    @ReportField(label = "Validity (months)")
    private Integer validityMonths;

    private Boolean mandatory;

    /** Internal remarks of the training team: never reportable. */
    @ReportHidden
    private String internalNotes;
}
