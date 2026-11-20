package com.gucardev.dynamicreportsentity.personnel.internal;

import com.gucardev.dynamicreportsentity.personnel.Personnel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonnelRepository extends JpaRepository<Personnel, Long> {

    Optional<Personnel> findByUsername(String username);
}
