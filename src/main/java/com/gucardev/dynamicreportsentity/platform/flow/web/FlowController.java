package com.gucardev.dynamicreportsentity.platform.flow.web;

import com.gucardev.dynamicreportsentity.platform.api.ApiResponse;

import com.gucardev.dynamicreportsentity.platform.flow.core.Flow;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowRun;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowSchedule;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowService;
import com.gucardev.dynamicreportsentity.platform.flow.core.RunService;
import com.gucardev.dynamicreportsentity.platform.flow.core.RunStatus;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowTrigger;
import com.gucardev.dynamicreportsentity.platform.flow.core.ScheduleService;
import com.gucardev.dynamicreportsentity.platform.flow.core.TriggerService;
import com.gucardev.dynamicreportsentity.platform.flow.web.FlowRequests.FlowRequest;
import com.gucardev.dynamicreportsentity.platform.flow.web.FlowRequests.ScheduleRequest;
import com.gucardev.dynamicreportsentity.platform.flow.web.FlowViews.FlowDetail;
import com.gucardev.dynamicreportsentity.platform.flow.web.FlowViews.FlowSummary;
import com.gucardev.dynamicreportsentity.platform.flow.web.FlowViews.RunSummary;
import com.gucardev.dynamicreportsentity.platform.flow.web.FlowViews.ScheduleView;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

import static com.gucardev.dynamicreportsentity.platform.flow.web.WebSupport.etag;
import static com.gucardev.dynamicreportsentity.platform.flow.web.WebSupport.expectedVersion;
import static com.gucardev.dynamicreportsentity.platform.flow.web.WebSupport.paging;
import static com.gucardev.dynamicreportsentity.platform.flow.web.WebSupport.viewer;

/**
 * Flows, plus the runs and schedules listed and created under them. Contract: {@code api/flows-api.yaml}.
 */
@RestController
@RequestMapping("/api/flows")
@PreAuthorize("hasAuthority('PLATFORM_USER')")
class FlowController {

    private final FlowService flows;
    private final RunService runs;
    private final ScheduleService schedules;
    private final TriggerService triggers;

    FlowController(FlowService flows, RunService runs, ScheduleService schedules, TriggerService triggers) {
        this.triggers = triggers;
        this.flows = flows;
        this.runs = runs;
        this.schedules = schedules;
    }

    /** Own flows (all flows for PLATFORM_ADMIN), last changed first. */
    @GetMapping
    ApiResponse<List<FlowSummary>> list(@RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer size, Authentication authentication) {
        int[] paging = paging(page, size);
        return ApiResponse.page(flows.list(viewer(authentication), paging[0], paging[1]), FlowViews::summary);
    }

    @PostMapping
    ResponseEntity<ApiResponse<FlowDetail>> create(@Valid @RequestBody FlowRequest request,
                                                   Authentication authentication) {
        Flow flow = flows.create(request.input(), viewer(authentication));
        return ResponseEntity.created(URI.create("/api/flows/" + flow.getId()))
                .eTag(etag(flow.getVersion()))
                .body(ApiResponse.of(FlowViews.detail(flow)));
    }

    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<FlowDetail>> get(@PathVariable long id, Authentication authentication) {
        Flow flow = flows.get(id, viewer(authentication));
        return ResponseEntity.ok().eTag(etag(flow.getVersion())).body(ApiResponse.of(FlowViews.detail(flow)));
    }

    /** Replaces the whole flow (name, description, steps); needs the ETag in If-Match. */
    @PutMapping("/{id}")
    ResponseEntity<ApiResponse<FlowDetail>> update(@PathVariable long id,
                                                   @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                   @Valid @RequestBody FlowRequest request,
                                                   Authentication authentication) {
        long expected = expectedVersion(ifMatch);
        Flow flow = flows.update(id, expected, request.input(), viewer(authentication));
        return ResponseEntity.ok().eTag(etag(flow.getVersion())).body(ApiResponse.of(FlowViews.detail(flow)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id, Authentication authentication) {
        flows.delete(id, viewer(authentication));
    }

    // ---- runs of a flow -------------------------------------------------------------------------------

    /** Queues a run and answers at once: 202 with the run's URL to follow. */
    @PostMapping("/{id}/runs")
    ResponseEntity<ApiResponse<RunSummary>> start(@PathVariable long id, Authentication authentication) {
        FlowRun run = runs.start(id, viewer(authentication));
        return ResponseEntity.accepted()
                .location(URI.create("/api/flow-runs/" + run.getId()))
                .header(HttpHeaders.RETRY_AFTER, "2")
                .body(ApiResponse.of(FlowViews.summary(run)));
    }

    @GetMapping("/{id}/runs")
    ApiResponse<List<RunSummary>> runs(@PathVariable long id, @RequestParam(required = false) List<String> status,
                                       @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer size, Authentication authentication) {
        int[] paging = paging(page, size);
        List<RunStatus> statuses = status == null ? List.of()
                : status.stream().map(value -> WebSupport.parse(RunStatus.class, value, "status")).toList();
        return ApiResponse.page(runs.list(id, statuses, viewer(authentication), paging[0], paging[1]),
                FlowViews::summary);
    }

    // ---- event triggers of a flow ---------------------------------------------------------------------

    /** At most 10 per flow, so not paged. */
    @GetMapping("/{id}/triggers")
    ApiResponse<List<FlowViews.TriggerView>> triggers(@PathVariable long id, Authentication authentication) {
        return ApiResponse.of(triggers.list(id, viewer(authentication)).stream().map(FlowViews::trigger).toList());
    }

    /** The webhook secret is in this answer only (and after a rotation); store it on the calling side. */
    @PostMapping("/{id}/triggers")
    ResponseEntity<ApiResponse<FlowViews.TriggerWithSecret>> createTrigger(@PathVariable long id,
                                                                           @Valid @RequestBody FlowRequests.TriggerRequest request,
                                                                           Authentication authentication) {
        FlowTrigger trigger = triggers.create(id, request.input(), viewer(authentication));
        return ResponseEntity.created(URI.create("/api/flow-triggers/" + trigger.getId()))
                .eTag(etag(trigger.getVersion()))
                .body(ApiResponse.of(new FlowViews.TriggerWithSecret(FlowViews.trigger(trigger), trigger.getWebhookSecret())));
    }

    // ---- schedules of a flow --------------------------------------------------------------------------

    /** At most {@code flows.max-schedules-per-flow}, so not paged. */
    @GetMapping("/{id}/schedules")
    ApiResponse<List<ScheduleView>> schedules(@PathVariable long id, Authentication authentication) {
        return ApiResponse.of(schedules.list(id, viewer(authentication)).stream().map(FlowViews::schedule).toList());
    }

    @PostMapping("/{id}/schedules")
    ResponseEntity<ApiResponse<ScheduleView>> createSchedule(@PathVariable long id,
                                                             @Valid @RequestBody ScheduleRequest request,
                                                             Authentication authentication) {
        FlowSchedule schedule = schedules.create(id, request.input(), viewer(authentication));
        return ResponseEntity.created(URI.create("/api/flow-schedules/" + schedule.getId()))
                .eTag(etag(schedule.getVersion()))
                .body(ApiResponse.of(FlowViews.schedule(schedule)));
    }
}
