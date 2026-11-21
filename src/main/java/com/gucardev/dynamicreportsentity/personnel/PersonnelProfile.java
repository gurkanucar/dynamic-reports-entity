package com.gucardev.dynamicreportsentity.personnel;

import com.gucardev.dynamicreportsentity.platform.dataset.DatasetField;
import com.gucardev.dynamicreportsentity.platform.dataset.DatasetReference;
import com.gucardev.dynamicreportsentity.platform.dataset.DatasetEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** One-to-one with Personnel (unique personnel_id): Personnel.profile is single-valued, no fan-out. */
@Entity
@Table(name = "personnel_profile")
@DatasetEntity(label = "Profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonnelProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @DatasetReference(target = "Personnel", oneToOne = true, owner = true, inverse = "profile", inverseLabel = "Profile")
    @Column(nullable = false, unique = true)
    private Long personnelId;

    @DatasetField(label = "Birth Date")
    private LocalDate birthDate;

    @DatasetField(label = "Blood Type")
    private String bloodType;

    @DatasetField(label = "City")
    private String city;

    @DatasetField(label = "Emergency Contact")
    private String emergencyContact;
}
