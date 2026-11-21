package com.gucardev.dynamicreportsentity.platform.approval;

import com.gucardev.dynamicreportsentity.platform.api.ListQuery;

import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.Kind;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import com.gucardev.dynamicreportsentity.platform.identity.UserDirectory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Approval groups (managed by admins) and requests. A request is visible to the members of its group, to whoever
 * asked for it or it runs for, and to admins; only group members decide.
 * <p>
 * Other modules use {@link #open}, {@link #cancel} and {@link #groupExists}, and learn about decisions from
 * {@link ApprovalDecided} events.
 */
@Service
public class ApprovalService {

    public static final String ADMIN_AUTHORITY = "PLATFORM_ADMIN";

    /** A group as entered by an admin. */
    public record GroupInput(String name, String description, List<ApprovalGroup.Member> members) {
    }

    private final ApprovalGroupRepository groups;
    private final ApprovalRequestRepository requests;
    private final List<ApprovalGroupUsage> usages;
    private final UserDirectory users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ApprovalService(ApprovalGroupRepository groups, ApprovalRequestRepository requests, List<ApprovalGroupUsage> usages,
                    UserDirectory users, ApplicationEventPublisher events, Clock clock) {
        this.groups = groups;
        this.requests = requests;
        this.usages = usages;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    // ---- for other modules ----------------------------------------------------------------------------

    public boolean groupExists(long id) {
        return groups.existsById(id);
    }

    /**
     * Opens a request, or returns the one already open for the same source (a retried caller gets the same request).
     *
     * @throws ApprovalException {@code APPROVAL_GROUP_NOT_FOUND} or {@code APPROVAL_GROUP_EMPTY}
     */
    @Transactional
    public ApprovalRequest open(NewApproval request) {
        ApprovalRequest existing = requests.findBySourceTypeAndSourceId(request.sourceType(), request.sourceId())
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        ApprovalGroup group = groups.findById(request.groupId()).orElseThrow(() -> new ApprovalException(
                Kind.CONFLICT, "APPROVAL_GROUP_NOT_FOUND", "Approval group " + request.groupId() + " no longer exists"));
        if (group.getMembers().isEmpty()) {
            throw new ApprovalException(Kind.CONFLICT, "APPROVAL_GROUP_EMPTY",
                    "Approval group '" + group.getName() + "' has no members");
        }
        ApprovalRequest opened = requests.saveAndFlush(new ApprovalRequest(request, group, clock.instant()));
        events.publishEvent(new ApprovalRequested(opened.getId(), request.notifyMembers()));
        return opened;
    }

    /** Withdraws a pending request (its source no longer waits); anything else is left as it is. */
    @Transactional
    public void cancel(String sourceType, String sourceId) {
        requests.lockBySource(sourceType, sourceId)
                .filter(request -> request.getStatus() == ApprovalRequest.Status.PENDING)
                .ifPresent(request -> request.cancelled(clock.instant()));
    }

    /**
     * Times out pending requests past their deadline; every instance may run this, each gets other rows.
     *
     * @return how many were timed out
     */
    @Transactional
    public int timeOutOverdue(int limit) {
        Instant now = clock.instant();
        List<Long> overdue = requests.lockOverdue(now, limit);
        for (Long id : overdue) {
            ApprovalRequest request = requests.findById(id).orElseThrow();
            request.timedOut(now);
            events.publishEvent(decided(request, ApprovalDecided.Outcome.TIMED_OUT));
        }
        return overdue.size();
    }

    // ---- groups ---------------------------------------------------------------------------------------

    public List<ApprovalGroup> groups() {
        return groups.findAllByOrderByNameAsc();
    }

    public ApprovalGroup group(long id) {
        return groups.findById(id).orElseThrow(() -> groupNotFound(id));
    }

    @Transactional
    public ApprovalGroup createGroup(GroupInput input, Actor admin) {
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
    public ApprovalGroup updateGroup(long id, long expectedVersion, GroupInput input, Actor admin) {
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
    public void deleteGroup(long id, Actor admin) {
        requireAdmin(admin);
        ApprovalGroup group = group(id);
        if (usages.stream().anyMatch(usage -> usage.usesGroup(id))
                || requests.existsByGroupIdAndStatus(id, ApprovalRequest.Status.PENDING)) {
            throw new ApprovalException(Kind.CONFLICT, "GROUP_IN_USE",
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
            throw ApprovalException.invalid(problems);
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
    /** Sortable fields of the request list (API name → property). */
    public static final Map<String, String> REQUEST_SORTS = Map.of("createdAt", "createdAt", "dueAt", "dueAt",
            "title", "title", "status", "status", "group", "groupName", "id", "id");

    public Page<ApprovalRequest> requests(Actor user, String scope, Collection<ApprovalRequest.Status> statuses,
                                          int page, int size) {
        return requests(user, scope, statuses, List.of(), null, null, page, size);
    }

    /**
     * @param groupIds only these groups (empty: all the user may see)
     * @param q        free text over title and group name
     * @param sort     e.g. {@code -createdAt}, {@code dueAt}; default newest first
     */
    public Page<ApprovalRequest> requests(Actor user, String scope, Collection<ApprovalRequest.Status> statuses,
                                          Collection<Long> groupIds, String q, String sort, int page, int size) {
        boolean all = "all".equals(scope);
        if (all) {
            requireAdmin(user);
        }
        Set<Long> memberOf = groups.findIdsByMember(user.username());
        Collection<Long> onlyGroups = groupIds;
        Collection<Long> memberGroups = memberOf.isEmpty() ? List.of(-1L) : memberOf;   // "in ()" is not valid SQL
        return requests.search(all, memberGroups, statuses.isEmpty() ? List.of(ApprovalRequest.Status.values()) : statuses,
                onlyGroups.isEmpty(), onlyGroups.isEmpty() ? List.of(-1L) : onlyGroups, ListQuery.like(q),
                ListQuery.page(page, size, ListQuery.sort(sort, REQUEST_SORTS, "-createdAt")));
    }

    public ApprovalRequest request(long id, Actor user) {
        ApprovalRequest request = requests.findById(id).orElseThrow(() -> requestNotFound(id));
        if (!isVisible(request, user)) {
            throw requestNotFound(id);
        }
        return request;
    }

    private boolean isVisible(ApprovalRequest request, Actor user) {
        return user.hasAuthority(ADMIN_AUTHORITY) || request.isRequestedBy(user.username())
                || groups.findIdsByMember(user.username()).contains(request.getGroupId());
    }

    /** Whether {@code user} may decide the request now (shown as buttons in the UI). */
    /**
     * Why the user cannot decide the request now, for the UI ({@code decided}, {@code not_member}, {@code own_request});
     * null when they can.
     */
    public String decisionBlocker(ApprovalRequest request, Actor user) {
        if (request.getStatus() != ApprovalRequest.Status.PENDING) {
            return "decided";
        }
        if (!groups.findIdsByMember(user.username()).contains(request.getGroupId())) {
            return "not_member";
        }
        return request.isAllowSelfApproval() || !request.isRequestedBy(user.username()) ? null : "own_request";
    }

    public boolean canDecide(ApprovalRequest request, Actor user) {
        return request.getStatus() == ApprovalRequest.Status.PENDING
                && groups.findIdsByMember(user.username()).contains(request.getGroupId())
                && (request.isAllowSelfApproval() || !request.isRequestedBy(user.username()));
    }

    /**
     * Records a decision. The request row is locked, so of two members deciding at once one wins and the other gets
     * {@code APPROVAL_ALREADY_DECIDED}. Whoever waits learns it from {@link ApprovalDecided}.
     */
    @Transactional
    public ApprovalRequest decide(long id, boolean approve, String comment, Actor user) {
        // locked before it is read at all: a request read earlier in this transaction would not be refreshed
        ApprovalRequest request = requests.lock(id).orElseThrow(() -> requestNotFound(id));
        if (!isVisible(request, user)) {
            throw requestNotFound(id);
        }
        if (!groups.findIdsByMember(user.username()).contains(request.getGroupId())) {
            throw new ApprovalException(Kind.FORBIDDEN, "NOT_APPROVER",
                    "Only members of '" + request.getGroupName() + "' may decide this request.");
        }
        if (request.getStatus() != ApprovalRequest.Status.PENDING) {
            throw new ApprovalException(Kind.CONFLICT, "APPROVAL_ALREADY_DECIDED",
                    "The request is already " + request.getStatus().name().toLowerCase() + ".");
        }
        if (!request.isAllowSelfApproval() && request.isRequestedBy(user.username())) {
            throw new ApprovalException(Kind.FORBIDDEN, "SELF_APPROVAL_FORBIDDEN",
                    "You asked for this approval or it runs for you; someone else from the group has to decide.");
        }
        request.decide(approve, user.username(), comment == null || comment.isBlank() ? null : comment.strip(),
                clock.instant());
        events.publishEvent(decided(request,
                approve ? ApprovalDecided.Outcome.APPROVED : ApprovalDecided.Outcome.REJECTED));
        return request;
    }

    // ---- helpers --------------------------------------------------------------------------------------

    private static ApprovalDecided decided(ApprovalRequest request, ApprovalDecided.Outcome outcome) {
        return new ApprovalDecided(request.getId(), request.getSourceType(), request.getSourceId(), outcome,
                request.getDecidedBy(), request.getComment(), request.getDecidedAt());
    }

    private static void requireAdmin(Actor user) {
        if (!user.hasAuthority(ADMIN_AUTHORITY)) {
            throw new ApprovalException(Kind.FORBIDDEN, "INSUFFICIENT_ROLE",
                    "Only dataset admins manage approval groups.");
        }
    }

    private static ApprovalException groupNotFound(long id) {
        return ApprovalException.notFound("APPROVAL_GROUP_NOT_FOUND", "Approval group " + id + " does not exist.");
    }

    private static ApprovalException requestNotFound(long id) {
        return ApprovalException.notFound("APPROVAL_NOT_FOUND", "Approval request " + id + " does not exist.");
    }

    private static ApprovalException nameTaken() {
        return new ApprovalException(Kind.CONFLICT, "GROUP_NAME_TAKEN", "Another approval group already has this name.");
    }

    private static ApprovalException versionMismatch() {
        return new ApprovalException(Kind.PRECONDITION_FAILED, "VERSION_MISMATCH",
                "The group was changed by someone else; reload it and try again.");
    }
}
