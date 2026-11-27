package com.gucardev.dynamicreportsentity.project;

import com.gucardev.entityreports.DatasetEntity;
import com.gucardev.entityreports.DatasetField;
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

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "project", indexes = @Index(name = "ix_project_department_id", columnList = "department_id"))
@DatasetEntity(label = "Project")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Version
    private long version;

    @DatasetField(label = "Code")
    @Column(nullable = false, unique = true)
    private String code;

    @DatasetField(label = "Name")
    private String name;

    @DatasetField(label = "Status")
    private String status;

    @DatasetField(label = "Budget")
    private BigDecimal budget;

    @DatasetField(label = "Start Date")
    private LocalDate startDate;

    @DatasetField(label = "End Date")
    private LocalDate endDate;

    @DatasetReference(target = "Department", label = "Owning Department", inverse = "projects", inverseLabel = "Projects")
    private Long departmentId;

    Project(String code) {
        this.code = code;
    }

    void define(String code, String name, String status, BigDecimal budget, LocalDate startDate, LocalDate endDate,
                Long departmentId) {
        this.code = code;
        this.name = name;
        this.status = status;
        this.budget = budget;
        this.startDate = startDate;
        this.endDate = endDate;
        this.departmentId = departmentId;
    }
}
