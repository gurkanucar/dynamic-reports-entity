package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.approval.ApprovalGroup;
import com.gucardev.dynamicreportsentity.platform.flow.approval.ApprovalGroupRepository;
import com.gucardev.dynamicreportsentity.platform.flow.approval.ApprovalRequest;
import com.gucardev.dynamicreportsentity.platform.flow.approval.ApprovalRequestRepository;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowException.Kind;
import com.gucardev.dynamicreportsentity.platform.reporting.ReportViewer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Approval groups (managed by report admins) and the requests approval steps open. A request is visible to the
 * members of its group, to whoever started the run or owns the flow, and to report admins; only group members decide.
 */
@Service
public class ApprovalService {

    /** A group as entered by an admin. */
    public record GroupInput(String name, String description, List<ApprovalGroup.Member> members) {
    }

    private final ApprovalGroupRepository groups;
    private final ApprovalRequestRepository requests;
    private final FlowRepository flows;
    private final RunStore store;
    private final RunAsResolver users;
    private final FlowEngine engine;
    private final Clock clock;

    ApprovalService(ApprovalGroupRepository groups, ApprovalRequestRepository requests, FlowRepository flows,
                    RunStore store, RunAsResolver users, FlowEngine engine, Clock clock) {
        this.groups = groups;
        this.requests = requests;
        this.flows = flows;
        this.store = store;
        this.users = users;
        this.engine = engine;
        this.clock = clock;
    }

    // ---- groups ---------------------------------------------------------------------------------------

    public List<ApprovalGroup> groups() {
        return groups.findAllByOrderByNameAsc();
    }

    public ApprovalGroup group(long id) {
        return groups.findById(id).orElseThrow(() -> groupNotFound(id));
    }

    @Transactional
    public ApprovalGroup createGroup(GroupInput input, ReportViewer admin) {
        requireAdmin(admin);
        check(input, null);
        ApprovalGroup group = new ApprovalGroup(clock.instant());
        group.define(input.name().strip(), input.description(), normalized(input.members()), admin.username(),
                clock.instant());
        try {
            return groups.saveAndFlush(group);
        } catch (DataIntegrityViolationException e) {
            throw nameTaken();
        }
    }

    @Transactional
    public ApprovalGroup updateGroup(long id, long expectedVersion, GroupInput input, ReportViewer admin) {
        requireAdmin(admin);
        ApprovalGroup group = group(id);
        if (group.getVersion() != expectedVersion) {
            throw versionMismatch();
        }
        check(input, id);
        group.define(input.name().strip(), input.description(), normalized(input.members()), admin.username(),
                clock.instant());
        try {
            return groups.saveAndFlush(group);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw versionMismatch();
        } catch (DataIntegrityViolationException e) {
            throw nameTaken();
        }
    }

    @Transactional
    public void deleteGroup(long id, ReportViewer admin) {
        requireAdmin(admin);
        ApprovalGroup group = group(id);
        if (flows.existsByApprovalGroupIdsContaining(id)
                || requests.existsByGroupIdAndStatus(id, ApprovalRequest.Status.PENDING)) {
            throw new FlowException(Kind.CONFLICT, "GROUP_IN_USE",
                    "The group is used by a flow or has pending requests.");
        }
        groups.delete(group);
    }

    private void check(GroupInput input, Long id) {
        List<FieldProblem> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<ApprovalGroup.Member> members = input.members() == null ? List.of() : input.members();
        for (int i = 0; i < members.size(); i++) {
            String username = members.get(i).username() == null ? "" : members.get(i).username().strip();
            if (!seen.add(username)) {
                problems.add(new FieldProblem("members[" + i + "].username", "DUPLICATE_MEMBER",
                        username + " is listed twice."));
            } else if (users.find(username).isEmpty()) {
                problems.add(new FieldProblem("members[" + i + "].username", "UNKNOWN_USER",
                        "There is no active user '" + username + "'."));
            }
        }
        if (!problems.isEmpty()) {
            throw FlowException.invalid(problems);
        }
        String name = input.name().strip();
        if (id == null ? groups.existsByNameIgnoreCase(name) : groups.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw nameTaken();
        }
    }

    private static List<ApprovalGroup.Member> normalized(List<ApprovalGroup.Member> members) {
        return members == null ? List.of() : members.stream().map(member -> new ApprovalGroup.Member(
                member.username().strip(), member.email() == null || member.email().isBlank() ? null
                : member.email().strip())).toList();
    }

    // ---- requests -------------------------------------------------------------------------------------

    /**
     * @param scope {@code mine}: requests the user can decide (their groups); {@code all}: every request (admins)
     */
    public Page<ApprovalRequest> requests(ReportViewer user, String scope, Collection<ApprovalRequest.Status> statuses,
                                          int page, int size) {
        boolean all = "all".equals(scope);
        if (all) {
            requireAdmin(user);
        }
        Set<Long> memberOf = groups.findIdsByMember(user.username());
        Collection<Long> groupIds = memberOf.isEmpty() ? List.of(-1L) : memberOf;   // "in ()" is not valid SQL
        return requests.search(all, groupIds, statuses.isEmpty() ? List.of(ApprovalRequest.Status.values()) : statuses,
                PageRequest.of(page - 1, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    }

    public ApprovalRequest request(long id, ReportViewer user) {
        ApprovalRequest request = requests.findById(id).orElseThrow(() -> requestNotFound(id));
        boolean visible = user.hasAuthority(FlowService.ADMIN_AUTHORITY) || request.isRequestedBy(user.username())
                || groups.findIdsByMember(user.username()).contains(request.getGroupId());
        if (!visible) {
            throw requestNotFound(id);
        }
        return request;
    }

    /** Whether {@code user} may decide the request now (shown as buttons in the UI). */
    public boolean canDecide(ApprovalRequest request, ReportViewer user) {
        return request.getStatus() == ApprovalRequest.Status.PENDING
                && groups.findIdsByMember(user.username()).contains(request.getGroupId())
                && (request.isAllowSelfApproval() || !request.isRequestedBy(user.username()));
    }

    public ApprovalRequest decide(long id, boolean approve, String comment, ReportViewer user) {
        request(id, user);   // 404 for requests the user may not even see
        ApprovalRequest decided = store.decide(id, approve, comment == null || comment.isBlank() ? null : comment.strip(),
                user.username(), groups.findIdsByMember(user.username()));
        engine.wakeUp();
        return decided;
    }

    // ---- helpers --------------------------------------------------------------------------------------

    private static void requireAdmin(ReportViewer user) {
        if (!user.hasAuthority(FlowService.ADMIN_AUTHORITY)) {
            throw new FlowException(Kind.FORBIDDEN, "INSUFFICIENT_ROLE", "Only report admins manage approval groups.");
        }
    }

    private static FlowException groupNotFound(long id) {
        return FlowException.notFound("APPROVAL_GROUP_NOT_FOUND", "Approval group " + id + " does not exist.");
    }

    private static FlowException requestNotFound(long id) {
        return FlowException.notFound("APPROVAL_NOT_FOUND", "Approval request " + id + " does not exist.");
    }

    private static FlowException nameTaken() {
        return new FlowException(Kind.CONFLICT, "GROUP_NAME_TAKEN", "Another approval group already has this name.");
    }

    private static FlowException versionMismatch() {
        return new FlowException(Kind.PRECONDITION_FAILED, "VERSION_MISMATCH",
                "The group was changed by someone else; reload it and try again.");
    }
}
