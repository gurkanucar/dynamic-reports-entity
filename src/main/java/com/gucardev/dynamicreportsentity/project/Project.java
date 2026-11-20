package com.gucardev.dynamicreportsentity.project;

import com.gucardev.dynamicreportsentity.reporting.ReportField;
import com.gucardev.dynamicreportsentity.reporting.ReportReference;
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

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "project")
@Reportable(label = "Project")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ReportField(label = "Code")
    @Column(nullable = false, unique = true)
    private String code;

    @ReportField(label = "Name")
    private String name;

    @ReportField(label = "Status")
    private String status;

    @ReportField(label = "Budget")
    private BigDecimal budget;

    @ReportField(label = "Start Date")
    private LocalDate startDate;

    @ReportField(label = "End Date")
    private LocalDate endDate;

    @ReportReference(target = "Department", label = "Owning Department", inverse = "projects", inverseLabel = "Projects")
    private Long departmentId;
}
