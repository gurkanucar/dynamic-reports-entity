package com.gucardev.dynamicreportsentity.training;

import com.gucardev.dynamicreportsentity.reporting.ReportField;
import com.gucardev.dynamicreportsentity.reporting.Reportable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Course catalog entry. */
@Entity
@Table(name = "training")
@Reportable(label = "Training")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Training {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ReportField(label = "Name")
    @Column(nullable = false)
    private String name;

    @ReportField(label = "Category")
    private String category;

    /** Months until renewal; empty if the certificate never expires. */
    @ReportField(label = "Validity (months)")
    private Integer validityMonths;

    @ReportField(label = "Mandatory")
    private Boolean mandatory;
}
