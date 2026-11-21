package com.gucardev.dynamicreportsentity.platform.dataset.web;

import java.util.UUID;
import com.gucardev.dynamicreportsentity.platform.api.ApiResponse;
import com.gucardev.dynamicreportsentity.platform.dataset.saved.SavedDatasetInfo;
import com.gucardev.dynamicreportsentity.platform.dataset.saved.SavedDatasetRequest;
import com.gucardev.dynamicreportsentity.platform.dataset.saved.SavedDatasetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Saved datasets: definitions stored under a name, used by flows like variables. To see the rows, run the definition
 * with {@code POST /api/datasets/run} (always live, with the caller's permissions). Anyone may save; the owner or an
 * admin may change or delete. Contract: {@code api/datasets-api.yaml}.
 */
@RestController
@RequestMapping("/api/saved-datasets")
@PreAuthorize("hasAuthority('PLATFORM_USER')")
class SavedDatasetController {

    private final SavedDatasetService datasets;

    SavedDatasetController(SavedDatasetService datasets) {
        this.datasets = datasets;
    }

    @GetMapping
    ApiResponse<List<SavedDatasetInfo>> list() {
        return ApiResponse.of(datasets.list());
    }

    @GetMapping("/{id}")
    ApiResponse<SavedDatasetInfo> get(@PathVariable UUID id) {
        return ApiResponse.of(datasets.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<SavedDatasetInfo> create(@Valid @RequestBody SavedDatasetRequest request, Authentication authentication) {
        return ApiResponse.of(datasets.create(request, DatasetController.viewer(authentication)));
    }

    @PutMapping("/{id}")
    ApiResponse<SavedDatasetInfo> update(@PathVariable UUID id, @Valid @RequestBody SavedDatasetRequest request,
                                         Authentication authentication) {
        return ApiResponse.of(datasets.update(id, request, DatasetController.viewer(authentication)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, Authentication authentication) {
        datasets.delete(id, DatasetController.viewer(authentication));
    }
}
