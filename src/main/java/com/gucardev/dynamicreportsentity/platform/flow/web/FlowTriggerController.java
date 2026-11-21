package com.gucardev.dynamicreportsentity.platform.flow.web;

import com.gucardev.dynamicreportsentity.platform.api.ApiResponse;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowTrigger;
import com.gucardev.dynamicreportsentity.platform.flow.core.TriggerService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.gucardev.dynamicreportsentity.platform.flow.web.WebSupport.etag;
import static com.gucardev.dynamicreportsentity.platform.flow.web.WebSupport.expectedVersion;
import static com.gucardev.dynamicreportsentity.platform.flow.web.WebSupport.viewer;

/** A single event trigger, once created under its flow ({@code POST /api/flows/{id}/triggers}). */
@RestController
@RequestMapping("/api/flow-triggers")
@PreAuthorize("hasAuthority('REPORT_RUN')")
class FlowTriggerController {

    private final TriggerService triggers;

    FlowTriggerController(TriggerService triggers) {
        this.triggers = triggers;
    }

    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<FlowViews.TriggerView>> get(@PathVariable long id, Authentication authentication) {
        FlowTrigger trigger = triggers.get(id, viewer(authentication));
        return ResponseEntity.ok().eTag(etag(trigger.getVersion())).body(ApiResponse.of(FlowViews.trigger(trigger)));
    }

    @PutMapping("/{id}")
    ResponseEntity<ApiResponse<FlowViews.TriggerView>> update(@PathVariable long id,
                                                              @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                              @Valid @RequestBody FlowRequests.TriggerRequest request,
                                                              Authentication authentication) {
        long expected = expectedVersion(ifMatch);
        FlowTrigger trigger = triggers.update(id, expected, request.input(), viewer(authentication));
        return ResponseEntity.ok().eTag(etag(trigger.getVersion())).body(ApiResponse.of(FlowViews.trigger(trigger)));
    }

    /** A new webhook secret (shown once); the old one stops working at once. */
    @PostMapping("/{id}/rotate-secret")
    ApiResponse<FlowViews.TriggerWithSecret> rotateSecret(@PathVariable long id, Authentication authentication) {
        FlowTrigger trigger = triggers.rotateSecret(id, viewer(authentication));
        return ApiResponse.of(new FlowViews.TriggerWithSecret(FlowViews.trigger(trigger), trigger.getWebhookSecret()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id, Authentication authentication) {
        triggers.delete(id, viewer(authentication));
    }
}
