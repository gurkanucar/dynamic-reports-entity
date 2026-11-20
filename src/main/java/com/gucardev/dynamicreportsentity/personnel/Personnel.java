package com.gucardev.dynamicreportsentity.personnel;

import com.gucardev.dynamicreportsentity.reporting.ReportField;
import com.gucardev.dynamicreportsentity.reporting.ReportReference;
import com.gucardev.dynamicreportsentity.reporting.Reportable;
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
@Table(name = "personnel", indexes = {
        @Index(name = "ix_personnel_department_id", columnList = "department_id"),
        @Index(name = "ix_personnel_manager_id", columnList = "manager_id")
})
@Reportable(label = "Personnel")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Personnel {

    /** Holders see every department; everyone else only their own (see DepartmentRowPolicy). */
    public static final String ALL_DEPARTMENTS = "ALL_DEPARTMENTS";
    public static final String SALARY_VIEW = "SALARY_VIEW";
    public static final String NATIONAL_ID_VIEW = "NATIONAL_ID_VIEW";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Login name; links the authenticated user to their personnel record. */
    @ReportField(label = "Username")
    @Column(unique = true)
    private String username;

    @ReportField(label = "Full Name")
    @Column(nullable = false)
    private String fullName;

    @ReportField(label = "Email")
    private String email;

    @ReportField(label = "Hire Date")
    private LocalDate hireDate;

    @ReportField(label = "Active")
    private Boolean active;

    /** Visible only to users holding SALARY_VIEW – in columns, filters and sorting alike. */
    @ReportField(label = "Salary", authority = SALARY_VIEW)
    private BigDecimal salary;

    /** Sensitive: only users holding NATIONAL_ID_VIEW see or use it; everyone else does not even find it in the catalog. */
    @ReportField(label = "National ID", authority = NATIONAL_ID_VIEW)
    private String nationalId;

    /** Many-to-one into another module, by ID only. Inverse: Department.members (one-to-many). */
    @ReportReference(target = "Department", inverse = "members", inverseLabel = "Members")
    private Long departmentId;

    /** Self-reference: manager.fullName, directReports.fullName */
    @ReportReference(target = "Personnel", label = "Manager", inverse = "directReports", inverseLabel = "Direct Reports")
    private Long managerId;
}
