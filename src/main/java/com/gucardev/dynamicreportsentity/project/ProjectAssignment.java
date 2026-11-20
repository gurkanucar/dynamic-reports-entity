package com.gucardev.dynamicreportsentity.project;

import com.gucardev.dynamicreportsentity.platform.reporting.ReportField;
import com.gucardev.dynamicreportsentity.platform.reporting.ReportReference;
import com.gucardev.dynamicreportsentity.platform.reporting.Reportable;
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

/** Many-to-many WITH extra columns across modules: Project ⇄ Personnel, with role and allocation. */
@Entity
@Table(name = "project_assignment", indexes = {
        @Index(name = "ix_project_assignment_project_id", columnList = "project_id"),
        @Index(name = "ix_project_assignment_personnel_id", columnList = "personnel_id")
})
@Reportable(label = "Project Assignment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ReportReference(target = "Project", inverse = "assignments", inverseLabel = "Assignments")
    @Column(nullable = false)
    private Long projectId;

    @ReportReference(target = "Personnel", owner = true, inverse = "projectAssignments", inverseLabel = "Project Assignments")
    @Column(nullable = false)
    private Long personnelId;

    @ReportField(label = "Role")
    private String role;

    @ReportField(label = "Allocation (%)")
    private Integer allocationPercent;

    @ReportField(label = "Assigned On")
    private LocalDate assignedOn;
}
