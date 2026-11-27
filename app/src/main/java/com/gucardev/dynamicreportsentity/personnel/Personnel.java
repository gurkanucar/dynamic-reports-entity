package com.gucardev.dynamicreportsentity.personnel;

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
@Table(name = "personnel", indexes = {
        @Index(name = "ix_personnel_department_id", columnList = "department_id"),
        @Index(name = "ix_personnel_manager_id", columnList = "manager_id")
})
@DatasetEntity(label = "Personnel")
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

    @jakarta.persistence.Version
    private long version;

    /** Login name; links the authenticated user to their personnel record. */
    @DatasetField(label = "Username")
    @Column(unique = true)
    private String username;

    @DatasetField(label = "Full Name")
    @Column(nullable = false)
    private String fullName;

    @DatasetField(label = "Email")
    private String email;

    @DatasetField(label = "Hire Date")
    private LocalDate hireDate;

    @DatasetField(label = "Active")
    private Boolean active;

    /** Visible only to users holding SALARY_VIEW – in columns, filters and sorting alike. */
    @DatasetField(label = "Salary", authority = SALARY_VIEW)
    private BigDecimal salary;

    /** Sensitive: only users holding NATIONAL_ID_VIEW see or use it; everyone else does not even find it in the catalog. */
    @DatasetField(label = "National ID", authority = NATIONAL_ID_VIEW)
    private String nationalId;

    /** Many-to-one into another module, by ID only. Inverse: Department.members (one-to-many). */
    @DatasetReference(target = "Department", inverse = "members", inverseLabel = "Members")
    private Long departmentId;

    /** Self-reference: manager.fullName, directReports.fullName */
    @DatasetReference(target = "Personnel", label = "Manager", inverse = "directReports", inverseLabel = "Direct Reports")
    private Long managerId;

    public Personnel(String fullName) {
        this.fullName = fullName;
    }

    /** Salary and national id are passed only by users who may see them; null keeps the current value. */
    public void define(String username, String fullName, String email, java.time.LocalDate hireDate, boolean active,
                       Long departmentId, Long managerId) {
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.hireDate = hireDate;
        this.active = active;
        this.departmentId = departmentId;
        this.managerId = managerId;
    }

    public void sensitive(BigDecimal salary, String nationalId, boolean salaryAllowed, boolean nationalIdAllowed) {
        if (salaryAllowed) {
            this.salary = salary;
        }
        if (nationalIdAllowed) {
            this.nationalId = nationalId;
        }
    }

    public void clearManager() {
        this.managerId = null;
    }
}
