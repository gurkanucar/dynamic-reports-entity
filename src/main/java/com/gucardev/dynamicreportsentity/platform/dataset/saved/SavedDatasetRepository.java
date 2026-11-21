package com.gucardev.dynamicreportsentity.platform.dataset.saved;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SavedDatasetRepository extends JpaRepository<SavedDataset, String> {

    List<SavedDataset> findAllByOrderByNameAsc();
}
