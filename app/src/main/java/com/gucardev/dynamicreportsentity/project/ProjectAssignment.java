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

import java.time.LocalDate;

/** Many-to-many WITH extra columns across modules: Project ⇄ Personnel, with role and allocation. */
@Entity
@Table(name = "project_assignment", indexes = {
        @Index(name = "ix_project_assignment_project_id", columnList = "project_id"),
        @Index(name = "ix_project_assignment_personnel_id", columnList = "personnel_id")
})
@DatasetEntity(label = "Project Assignment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Version
    private long version;

    @DatasetReference(target = "Project", inverse = "assignments", inverseLabel = "Assignments")
    @Column(nullable = false)
    private Long projectId;

    @DatasetReference(target = "Personnel", owner = true, inverse = "projectAssignments", inverseLabel = "Project Assignments")
    @Column(nullable = false)
    private Long personnelId;

    @DatasetField(label = "Role")
    private String role;

    @DatasetField(label = "Allocation (%)")
    private Integer allocationPercent;

    @DatasetField(label = "Assigned On")
    private LocalDate assignedOn;

    ProjectAssignment(Long projectId, Long personnelId) {
        this.projectId = projectId;
        this.personnelId = personnelId;
    }

    void define(Long personnelId, String role, Integer allocationPercent, LocalDate assignedOn) {
        this.personnelId = personnelId;
        this.role = role;
        this.allocationPercent = allocationPercent;
        this.assignedOn = assignedOn;
    }
}
