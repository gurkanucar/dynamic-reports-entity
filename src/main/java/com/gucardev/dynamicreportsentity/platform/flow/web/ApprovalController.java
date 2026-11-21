package com.gucardev.dynamicreportsentity.platform.flow.web;

import com.gucardev.dynamicreportsentity.platform.api.ApiResponse;
import com.gucardev.dynamicreportsentity.platform.flow.approval.ApprovalGroup;
import com.gucardev.dynamicreportsentity.platform.flow.approval.ApprovalRequest;
import com.gucardev.dynamicreportsentity.platform.flow.core.ApprovalService;
import com.gucardev.dynamicreportsentity.platform.reporting.ReportViewer;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

import static com.gucardev.dynamicreportsentity.platform.flow.web.WebSupport.etag;
import static com.gucardev.dynamicreportsentity.platform.flow.web.WebSupport.expectedVersion;
import static com.gucardev.dynamicreportsentity.platform.flow.web.WebSupport.paging;
import static com.gucardev.dynamicreportsentity.platform.flow.web.WebSupport.viewer;

/** Approval groups (report admins manage them) and the requests approval steps open. */
@RestController
@PreAuthorize("hasAuthority('REPORT_RUN')")
class ApprovalController {

    private final ApprovalService approvals;

    ApprovalController(ApprovalService approvals) {
        this.approvals = approvals;
    }

    // ---- groups (at most a few dozen: not paged) -------------------------------------------------------

    @GetMapping("/api/approval-groups")
    ApiResponse<List<FlowViews.GroupView>> groups() {
        return ApiResponse.of(approvals.groups().stream().map(FlowViews::group).toList());
    }

    @PostMapping("/api/approval-groups")
    ResponseEntity<ApiResponse<FlowViews.GroupView>> createGroup(@Valid @RequestBody FlowRequests.GroupRequest request,
                                                                 Authentication authentication) {
        ApprovalGroup group = approvals.createGroup(request.input(), viewer(authentication));
        return ResponseEntity.created(URI.create("/api/approval-groups/" + group.getId()))
                .eTag(etag(group.getVersion())).body(ApiResponse.of(FlowViews.group(group)));
    }

    @GetMapping("/api/approval-groups/{id}")
    ResponseEntity<ApiResponse<FlowViews.GroupView>> group(@PathVariable long id) {
        ApprovalGroup group = approvals.group(id);
        return ResponseEntity.ok().eTag(etag(group.getVersion())).body(ApiResponse.of(FlowViews.group(group)));
    }

    @PutMapping("/api/approval-groups/{id}")
    ResponseEntity<ApiResponse<FlowViews.GroupView>> updateGroup(@PathVariable long id,
                                                                 @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                                 @Valid @RequestBody FlowRequests.GroupRequest request,
                                                                 Authentication authentication) {
        long expected = expectedVersion(ifMatch);
        ApprovalGroup group = approvals.updateGroup(id, expected, request.input(), viewer(authentication));
        return ResponseEntity.ok().eTag(etag(group.getVersion())).body(ApiResponse.of(FlowViews.group(group)));
    }

    @DeleteMapping("/api/approval-groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteGroup(@PathVariable long id, Authentication authentication) {
        approvals.deleteGroup(id, viewer(authentication));
    }

    // ---- requests -------------------------------------------------------------------------------------

    /** {@code scope=mine} (default): requests of my groups; {@code all}: every request (report admins). */
    @GetMapping("/api/approval-requests")
    ApiResponse<List<FlowViews.ApprovalView>> requests(@RequestParam(defaultValue = "mine") String scope,
                                                      @RequestParam(required = false) List<String> status,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size,
                                                      Authentication authentication) {
        int[] paging = paging(page, size);
        if (!scope.equals("mine") && !scope.equals("all")) {
            throw new com.gucardev.dynamicreportsentity.platform.flow.core.FlowException(
                    com.gucardev.dynamicreportsentity.platform.flow.core.FlowException.Kind.BAD_REQUEST,
                    "INVALID_PARAMETER", "scope must be mine or all.");
        }
        ReportViewer user = viewer(authentication);
        List<ApprovalRequest.Status> statuses = status == null ? List.of()
                : status.stream().map(value -> WebSupport.parse(ApprovalRequest.Status.class, value, "status")).toList();
        return ApiResponse.page(approvals.requests(user, scope, statuses, paging[0], paging[1]),
                request -> FlowViews.approval(request, approvals.canDecide(request, user)));
    }

    @GetMapping("/api/approval-requests/{id}")
    ApiResponse<FlowViews.ApprovalView> request(@PathVariable long id, Authentication authentication) {
        ReportViewer user = viewer(authentication);
        ApprovalRequest request = approvals.request(id, user);
        return ApiResponse.of(FlowViews.approval(request, approvals.canDecide(request, user)));
    }

    @PostMapping("/api/approval-requests/{id}/approve")
    ApiResponse<FlowViews.ApprovalView> approve(@PathVariable long id,
                                                @Valid @RequestBody(required = false) FlowRequests.DecisionRequest body,
                                                Authentication authentication) {
        return decide(id, true, body, authentication);
    }

    @PostMapping("/api/approval-requests/{id}/reject")
    ApiResponse<FlowViews.ApprovalView> reject(@PathVariable long id,
                                               @Valid @RequestBody(required = false) FlowRequests.DecisionRequest body,
                                               Authentication authentication) {
        return decide(id, false, body, authentication);
    }

    private ApiResponse<FlowViews.ApprovalView> decide(long id, boolean approve, FlowRequests.DecisionRequest body,
                                                       Authentication authentication) {
        ReportViewer user = viewer(authentication);
        ApprovalRequest request = approvals.decide(id, approve, body == null ? null : body.comment(), user);
        return ApiResponse.of(FlowViews.approval(request, false));
    }
}
