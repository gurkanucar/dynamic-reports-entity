package com.gucardev.dynamicreportsentity.organization;

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

@Entity
@Table(name = "department")
@Reportable(label = "Department")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ReportField(label = "Name")
    @Column(nullable = false)
    private String name;

    @ReportField(label = "Cost Center")
    private String costCenter;

    /** Self-reference (hierarchy): parent.name, subDepartments.name */
    @ReportReference(target = "Department", label = "Parent Department",
            inverse = "subDepartments", inverseLabel = "Sub-departments")
    private Long parentId;

    /**
     * A second relation between Department and Personnel (besides Personnel.department).
     * Because every relation is named, "department.manager" and "members" never get mixed up.
     */
    @ReportReference(target = "Personnel", label = "Manager",
            inverse = "managedDepartments", inverseLabel = "Managed Departments")
    private Long managerId;
}
