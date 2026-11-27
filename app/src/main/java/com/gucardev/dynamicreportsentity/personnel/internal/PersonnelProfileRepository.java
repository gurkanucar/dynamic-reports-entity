package com.gucardev.dynamicreportsentity.personnel.internal;

import com.gucardev.dynamicreportsentity.personnel.PersonnelProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonnelProfileRepository extends JpaRepository<PersonnelProfile, Long> {

    Optional<PersonnelProfile> findByPersonnelId(Long personnelId);
}
