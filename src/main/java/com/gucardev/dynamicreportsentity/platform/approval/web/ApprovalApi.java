package com.gucardev.dynamicreportsentity.platform.approval.web;

import com.gucardev.dynamicreportsentity.platform.approval.ApprovalGroup;
import com.gucardev.dynamicreportsentity.platform.approval.ApprovalRequest;
import com.gucardev.dynamicreportsentity.platform.approval.ApprovalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.time;
import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.value;

/** Request and response bodies of the approval API. */
final class ApprovalApi {

    private ApprovalApi() {
    }

    record GroupRequest(@NotBlank @Size(max = 100) String name,
                        @Size(max = 1000) String description,
                        @Size(max = 200) List<@NotNull @Valid MemberRequest> members) {

        ApprovalService.GroupInput input() {
            return new ApprovalService.GroupInput(name, description, members == null ? List.of()
                    : members.stream().map(m -> new ApprovalGroup.Member(m.username(), m.email())).toList());
        }
    }

    record MemberRequest(@NotBlank @Size(max = 100) String username, @Email @Size(max = 254) String email) {
    }

    record DecisionRequest(@Size(max = 1000) String comment) {
    }

    record UserRef(String username) {
    }

    record GroupRef(long id, String name) {
    }

    record GroupView(long id, String name, String description, List<ApprovalGroup.Member> members, long version,
                     String updatedAt, UserRef updatedBy) {
    }

    /**
     * @param source what waits for the decision: its type and id, a label for people and an optional UI path
     */
    record ApprovalView(long id, SourceRef source, GroupRef group, String title, String description, String status,
                        String requestedBy, UserRef onBehalfOf, String createdAt, String dueAt, String decidedBy,
                        String decidedAt, String comment, boolean canDecide) {
    }

    record SourceRef(String type, String id, String label, String link) {
    }

    static GroupView group(ApprovalGroup group) {
        return new GroupView(group.getId(), group.getName(), group.getDescription(), List.copyOf(group.getMembers()),
                group.getVersion(), time(group.getUpdatedAt()), new UserRef(group.getUpdatedBy()));
    }

    static ApprovalView approval(ApprovalRequest request, boolean canDecide) {
        return new ApprovalView(request.getId(), new SourceRef(request.getSourceType(), request.getSourceId(),
                request.getSubject(), request.getLink()), new GroupRef(request.getGroupId(), request.getGroupName()),
                request.getTitle(), request.getDescription(), value(request.getStatus()), request.getRequestedBy(),
                new UserRef(request.getOnBehalfOf()), time(request.getCreatedAt()), time(request.getDueAt()),
                request.getDecidedBy(), time(request.getDecidedAt()), request.getComment(), canDecide);
    }
}
