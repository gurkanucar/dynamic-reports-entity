package com.gucardev.dynamicreportsentity.organization;

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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "department", indexes = {
        @Index(name = "ix_department_parent_id", columnList = "parent_id"),
        @Index(name = "ix_department_manager_id", columnList = "manager_id")
})
@DatasetEntity(label = "Department")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @DatasetField(label = "Name")
    @Column(nullable = false)
    private String name;

    @DatasetField(label = "Cost Center")
    private String costCenter;

    /** Self-reference (hierarchy): parent.name, subDepartments.name */
    @DatasetReference(target = "Department", label = "Parent Department",
            inverse = "subDepartments", inverseLabel = "Sub-departments")
    private Long parentId;

    /**
     * A second relation between Department and Personnel (besides Personnel.department).
     * Because every relation is named, "department.manager" and "members" never get mixed up.
     */
    @DatasetReference(target = "Personnel", label = "Manager",
            inverse = "managedDepartments", inverseLabel = "Managed Departments")
    private Long managerId;

    Department(String name) {
        this.name = name;
    }

    void define(String name, String costCenter, Long parentId, Long managerId) {
        this.name = name;
        this.costCenter = costCenter;
        this.parentId = parentId;
        this.managerId = managerId;
    }
}
