package com.gucardev.dynamicreportsentity.project;

import com.gucardev.dynamicreportsentity.platform.dataset.DatasetField;
import com.gucardev.dynamicreportsentity.platform.dataset.DatasetReference;
import com.gucardev.dynamicreportsentity.platform.dataset.DatasetEntity;
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
}
