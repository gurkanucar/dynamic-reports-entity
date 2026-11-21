package com.gucardev.dynamicreportsentity.platform.dataset.saved;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SavedDatasetRepository extends JpaRepository<SavedDataset, UUID> {

    List<SavedDataset> findAllByOrderByNameAsc();

    java.util.Optional<SavedDataset> findByName(String name);

    boolean existsByName(String name);
}
