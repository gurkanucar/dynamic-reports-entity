package com.gucardev.dynamicreportsentity.platform.flow.web;

import com.gucardev.dynamicreportsentity.platform.flow.core.ArtifactStore;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowRun;
import com.gucardev.dynamicreportsentity.platform.flow.core.RunArtifact;
import com.gucardev.dynamicreportsentity.platform.flow.core.RunService;
import com.gucardev.dynamicreportsentity.platform.flow.core.RunStatus;
import com.gucardev.dynamicreportsentity.platform.flow.web.FlowViews.RunDetail;
import com.gucardev.dynamicreportsentity.platform.flow.web.FlowViews.RunSummary;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static com.gucardev.dynamicreportsentity.platform.flow.web.WebSupport.paging;
import static com.gucardev.dynamicreportsentity.platform.flow.web.WebSupport.viewer;
import static java.nio.charset.StandardCharsets.UTF_8;

/** Runs (status, steps, history, cancel) and the files they produced. */
@RestController
@PreAuthorize("hasAuthority('REPORT_RUN')")
class FlowRunController {

    private final RunService runs;
    private final ArtifactStore artifacts;
    private final Clock clock;

    FlowRunController(RunService runs, ArtifactStore artifacts, Clock clock) {
        this.runs = runs;
        this.artifacts = artifacts;
        this.clock = clock;
    }

    /** Runs of every flow the user may see, newest first. */
    @GetMapping("/api/flow-runs")
    ApiResponse<List<RunSummary>> list(@RequestParam(required = false) List<String> status,
                                       @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer size, Authentication authentication) {
        int[] paging = paging(page, size);
        List<RunStatus> statuses = status == null ? List.of()
                : status.stream().map(value -> WebSupport.parse(RunStatus.class, value, "status")).toList();
        return ApiResponse.page(runs.list(null, statuses, viewer(authentication), paging[0], paging[1]),
                FlowViews::summary);
    }

    /** While the run is active, {@code Retry-After} suggests when to ask again. */
    @GetMapping("/api/flow-runs/{id}")
    ResponseEntity<ApiResponse<RunDetail>> get(@PathVariable long id, Authentication authentication) {
        FlowRun run = runs.get(id, viewer(authentication));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (!run.getStatus().isFinished()) {
            response.header(HttpHeaders.RETRY_AFTER, "2");
        }
        return response.body(ApiResponse.of(FlowViews.detail(run, runs.steps(id), runs.events(id), runs.files(id),
                clock.instant())));
    }

    /**
     * Waiting steps are cancelled at once; a running step is stopped (its query is cancelled in the database)
     * within a few seconds, then the run becomes {@code cancelled}. 409 if the run has already finished.
     */
    @PostMapping("/api/flow-runs/{id}/cancel")
    ApiResponse<RunSummary> cancel(@PathVariable long id, Authentication authentication) {
        return ApiResponse.of(FlowViews.summary(runs.cancel(id, viewer(authentication))));
    }

    /**
     * 302 to a short-lived presigned URL of the object store (the bytes do not pass through the app); streamed when
     * the store offers none ({@code storage.s3.presigned-downloads=false}, filesystem store). 410 once expired.
     */
    @GetMapping("/api/flow-files/{id}/download")
    ResponseEntity<StreamingResponseBody> download(@PathVariable long id, Authentication authentication) {
        RunArtifact file = runs.file(id, viewer(authentication));
        Optional<URI> direct = artifacts.downloadUrl(file);
        if (direct.isPresent()) {
            return ResponseEntity.status(HttpStatus.FOUND).location(direct.get())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.getFileName(), UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(file.getSizeBytes())
                .body(out -> {
                    try (InputStream in = artifacts.open(file)) {
                        in.transferTo(out);
                    }
                });
    }
}
