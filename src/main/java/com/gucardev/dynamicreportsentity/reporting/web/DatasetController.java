package com.gucardev.dynamicreportsentity.reporting.web;

import com.gucardev.dynamicreportsentity.reporting.dataset.DatasetInfo;
import com.gucardev.dynamicreportsentity.reporting.dataset.DatasetRequest;
import com.gucardev.dynamicreportsentity.reporting.dataset.DatasetRows;
import com.gucardev.dynamicreportsentity.reporting.dataset.DatasetService;
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
 * Saved reports that flows read by name. {@code /api/reports/run} stays the way to run an ad-hoc report;
 * a dataset is a report definition saved once, served LIVE (run on every read) or from a SNAPSHOT
 * refreshed every few hours.
 * <p>
 * Reading needs REPORT_RUN; defining, changing and refreshing needs REPORT_ADMIN (a snapshot is computed
 * over every row).
 */
@RestController
@RequestMapping("/api/datasets")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('REPORT_RUN')")
class DatasetController {

    private final DatasetService datasets;

    @GetMapping
    public List<DatasetInfo> list() {
        return datasets.list();
    }

    @GetMapping("/{name}")
    public DatasetInfo get(@PathVariable String name) {
        return datasets.get(name);
    }

    /** One page, same shape as {@code /api/reports/run} plus when the data was computed. */
    @GetMapping("/{name}/rows")
    public DatasetRows rows(@PathVariable String name,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(required = false) Integer size,
                            @RequestParam(defaultValue = "false") boolean includeTotal,
                            Authentication authentication) {
        return datasets.read(name, ReportController.viewer(authentication), page, size, includeTotal);
    }

    /** SNAPSHOT datasets start building in the background; poll {@code GET /{name}} for the status. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('REPORT_ADMIN')")
    public DatasetInfo create(@Valid @RequestBody DatasetRequest request, Authentication authentication) {
        return datasets.create(request, ReportController.viewer(authentication));
    }

    @PutMapping("/{name}")
    @PreAuthorize("hasAuthority('REPORT_ADMIN')")
    public DatasetInfo update(@PathVariable String name, @Valid @RequestBody DatasetRequest request,
                              Authentication authentication) {
        return datasets.update(name, request, ReportController.viewer(authentication));
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('REPORT_ADMIN')")
    public void delete(@PathVariable String name) {
        datasets.delete(name);
    }

    /** Rebuilds the snapshot now and answers when it is done. */
    @PostMapping("/{name}/refresh")
    @PreAuthorize("hasAuthority('REPORT_ADMIN')")
    public DatasetInfo refresh(@PathVariable String name) {
        return datasets.refresh(name);
    }
}
