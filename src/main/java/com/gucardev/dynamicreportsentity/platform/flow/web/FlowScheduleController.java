package com.gucardev.dynamicreportsentity.platform.flow.web;

import com.gucardev.dynamicreportsentity.platform.api.ApiResponse;

import com.gucardev.dynamicreportsentity.platform.flow.core.FlowSchedule;
import com.gucardev.dynamicreportsentity.platform.flow.core.ScheduleService;
import com.gucardev.dynamicreportsentity.platform.flow.web.FlowRequests.ScheduleRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.gucardev.dynamicreportsentity.platform.flow.web.WebSupport.etag;
import static com.gucardev.dynamicreportsentity.platform.flow.web.WebSupport.expectedVersion;
import static com.gucardev.dynamicreportsentity.platform.flow.web.WebSupport.viewer;

/** A single schedule, once created under its flow ({@code POST /api/flows/{id}/schedules}). */
@RestController
@RequestMapping("/api/flow-schedules")
@PreAuthorize("hasAuthority('PLATFORM_USER')")
class FlowScheduleController {

    private final ScheduleService schedules;

    FlowScheduleController(ScheduleService schedules) {
        this.schedules = schedules;
    }

    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<ScheduleView>> get(@PathVariable long id, Authentication authentication) {
        FlowSchedule schedule = schedules.get(id, viewer(authentication));
        return ResponseEntity.ok().eTag(etag(schedule.getVersion())).body(ApiResponse.of(FlowViews.schedule(schedule)));
    }

    @PutMapping("/{id}")
    ResponseEntity<ApiResponse<ScheduleView>> update(@PathVariable long id,
                                                     @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                     @Valid @RequestBody ScheduleRequest request,
                                                     Authentication authentication) {
        long expected = expectedVersion(ifMatch);
        FlowSchedule schedule = schedules.update(id, expected, request.input(), viewer(authentication));
        return ResponseEntity.ok().eTag(etag(schedule.getVersion())).body(ApiResponse.of(FlowViews.schedule(schedule)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id, Authentication authentication) {
        schedules.delete(id, viewer(authentication));
    }
}
