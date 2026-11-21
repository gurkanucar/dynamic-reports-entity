package com.gucardev.dynamicreportsentity.platform.dataset.web;

import com.gucardev.dynamicreportsentity.platform.dataset.snapshot.SnapshotInfo;
import com.gucardev.dynamicreportsentity.platform.dataset.snapshot.SnapshotRequest;
import com.gucardev.dynamicreportsentity.platform.dataset.snapshot.SnapshotRows;
import com.gucardev.dynamicreportsentity.platform.dataset.snapshot.SnapshotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Saved datasets that flows read by name. {@code /api/datasets/run} stays the way to run an ad-hoc dataset;
 * a snapshot is a dataset definition saved once, served LIVE (run on every read) or from a SNAPSHOT
 * refreshed every few hours.
 * <p>
 * Reading needs PLATFORM_USER; defining, changing and refreshing needs PLATFORM_ADMIN (a snapshot is computed
 * over every row).
 */
@RestController
@RequestMapping("/api/snapshots")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PLATFORM_USER')")
class SnapshotController {

    private final SnapshotService snapshots;

    @GetMapping
    public List<SnapshotInfo> list() {
        return snapshots.list();
    }

    @GetMapping("/{name}")
    public SnapshotInfo get(@PathVariable String name) {
        return snapshots.get(name);
    }

    /** One page, same shape as {@code /api/datasets/run} plus when the data was computed. */
    @GetMapping("/{name}/rows")
    public SnapshotRows rows(@PathVariable String name,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(required = false) Integer size,
                            @RequestParam(defaultValue = "false") boolean includeTotal,
                            Authentication authentication) {
        return snapshots.read(name, DatasetController.viewer(authentication), page, size, includeTotal);
    }

    /** SNAPSHOT snapshots start building in the background; poll {@code GET /{name}} for the status. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SnapshotInfo create(@Valid @RequestBody SnapshotRequest request, Authentication authentication) {
        return snapshots.create(request, DatasetController.viewer(authentication));
    }

    @PutMapping("/{name}")
    public SnapshotInfo update(@PathVariable String name, @Valid @RequestBody SnapshotRequest request,
                              Authentication authentication) {
        return snapshots.update(name, request, DatasetController.viewer(authentication));
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String name, Authentication authentication) {
        snapshots.delete(name, DatasetController.viewer(authentication));
    }

    /** Rebuilds the snapshot now and answers when it is done. */
    @PostMapping("/{name}/refresh")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    public SnapshotInfo refresh(@PathVariable String name) {
        return snapshots.refresh(name);
    }
}
