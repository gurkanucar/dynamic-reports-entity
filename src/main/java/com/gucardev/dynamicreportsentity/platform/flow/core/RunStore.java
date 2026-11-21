package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.approval.ApprovalRequest;
import com.gucardev.dynamicreportsentity.platform.flow.approval.ApprovalRequestRepository;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowException.Kind;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Every state change of runs and steps, one transaction each; built for many instances on one database.
 * <ul>
 *   <li>Picking work (claims, lease takeovers, schedule fires, file cleanup) locks rows with
 *       {@code FOR UPDATE SKIP LOCKED}: concurrent instances take different rows and never wait for each other.</li>
 *   <li>Changing a known run (step finished or failed, cancel) locks the run row first and waits for it, so these
 *       changes are applied one after the other, never interleaved. Picking never waits, so the two cannot
 *       deadlock.</li>
 *   <li>A step result is only accepted from the attempt that holds the claim token.</li>
 * </ul>
 */
@Component
class RunStore {

    static final int PRIORITY_MANUAL = 10;
    static final int PRIORITY_SCHEDULE = 0;
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(30);

    private final FlowRunRepository runs;
    private final StepRunRepository steps;
    private final RunEventRepository events;
    private final FlowScheduleRepository schedules;
    private final FlowRepository flows;
    private final ApprovalRequestRepository approvals;
    private final FlowTriggerRepository triggers;
    private final TriggerEventRepository triggerEvents;
    private final FlowProperties properties;
    private final Clock clock;

    RunStore(FlowRunRepository runs, StepRunRepository steps, RunEventRepository events,
             FlowScheduleRepository schedules, FlowRepository flows, ApprovalRequestRepository approvals,
             FlowTriggerRepository triggers, TriggerEventRepository triggerEvents, FlowProperties properties,
             Clock clock) {
        this.approvals = approvals;
        this.triggers = triggers;
        this.triggerEvents = triggerEvents;
        this.runs = runs;
        this.steps = steps;
        this.events = events;
        this.schedules = schedules;
        this.flows = flows;
        this.properties = properties;
        this.clock = clock;
    }

    // ---- starting -------------------------------------------------------------------------------------

    @Transactional
    FlowRun createRun(Flow flow, TriggerType trigger, String triggeredBy, Long scheduleId, int priority) {
        return newRun(flow, trigger, triggeredBy, scheduleId, priority, clock.instant(), null);
    }

    /** A run started by an event (webhook, Kafka); {@code payload} becomes {@code trigger} for its steps. */
    @Transactional
    FlowRun createRun(Flow flow, TriggerType trigger, String triggeredBy, Object payload) {
        return newRun(flow, trigger, triggeredBy, null, PRIORITY_SCHEDULE, clock.instant(),
                payload == null ? null : FlowDefinition.toJson(payload));
    }

    /**
     * Fires a due schedule: moves it to its next fire time and starts a run, in one transaction holding the schedule
     * row ({@code SKIP LOCKED}), so each fire time starts exactly one run however many instances see it due.
     */
    @Transactional
    Optional<FlowRun> fireSchedule(long scheduleId, Instant now) {
        if (schedules.lockIfDue(scheduleId, now).isEmpty()) {
            return Optional.empty();   // another instance is firing it, or already did
        }
        FlowSchedule schedule = schedules.findById(scheduleId).orElseThrow();
        Instant fireTime = schedule.getNextFireAt();
        Instant next = CronSchedules.next(schedule.getCron(), schedule.getTimeZone(), now);
        Flow flow = flows.findById(schedule.getFlowId()).orElse(null);
        if (flow == null) {
            schedule.fired(fireTime, null, null, "The flow no longer exists");
            return Optional.empty();
        }
        if (runs.existsByFlowIdAndStatusIn(flow.getId(), RunStatus.ACTIVE)) {
            schedule.fired(fireTime, next, null, "Skipped the run of " + fireTime
                    + ": the previous run of the flow was still active");
            return Optional.empty();
        }
        FlowRun run = newRun(flow, TriggerType.SCHEDULE, "schedule:" + schedule.getId(), schedule.getId(),
                PRIORITY_SCHEDULE, now, null);
        schedule.fired(fireTime, next, run.getId(), null);
        return Optional.of(run);
    }

    /**
     * Starts a run for an event, unless the same event (same {@code eventKey}) already started one: the inbox row is
     * written in this transaction, so a concurrent second delivery fails on the unique key and starts nothing.
     *
     * @param eventKey null = the sender gave no id; every delivery then starts a run
     * @return empty if the event was a duplicate or the flow is gone
     */
    @Transactional
    Optional<FlowRun> fireTrigger(FlowTrigger trigger, String eventKey, Object payload) {
        if (eventKey != null && triggerEvents.existsByTriggerIdAndEventKey(trigger.getId(), eventKey)) {
            return Optional.empty();
        }
        Flow flow = flows.findById(trigger.getFlowId()).orElse(null);
        if (flow == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        TriggerType type = trigger.getType() == FlowTrigger.Type.KAFKA ? TriggerType.KAFKA : TriggerType.WEBHOOK;
        FlowRun run = newRun(flow, type, type.name().toLowerCase() + ":" + trigger.getId(), null, PRIORITY_SCHEDULE,
                now, payload == null ? null : FlowDefinition.toJson(payload));
        if (eventKey != null) {
            triggerEvents.saveAndFlush(new TriggerEvent(trigger.getId(), eventKey, now, run.getId()));
        }
        triggers.markFired(trigger.getId(), now, run.getId());
        return Optional.of(run);
    }

    /** A schedule that failed to fire is moved to its next fire time, so it cannot block the others. */
    @Transactional
    void skipBrokenSchedule(long scheduleId, Instant now, String error) {
        if (schedules.lockIfDue(scheduleId, now).isEmpty()) {
            return;
        }
        FlowSchedule schedule = schedules.findById(scheduleId).orElseThrow();
        Instant next;
        try {
            next = CronSchedules.next(schedule.getCron(), schedule.getTimeZone(), now);
        } catch (RuntimeException e) {
            next = null;
        }
        schedule.fired(schedule.getNextFireAt(), next, null, Texts.truncate("Could not start the run: " + error, 300));
    }

    private FlowRun newRun(Flow flow, TriggerType trigger, String triggeredBy, Long scheduleId, int priority,
                           Instant now, String payloadJson) {
        FlowRun run = runs.save(new FlowRun(flow, trigger, triggeredBy, scheduleId, priority, now, payloadJson));
        FlowGraph graph = new FlowGraph(run.definition());
        FlowStep first = graph.start();
        steps.save(newStep(run, graph.indexOf(first.key()), first, now));
        event(run.getId(), now, "run_queued", null, switch (trigger) {
            case MANUAL -> "Started by " + triggeredBy;
            case SCHEDULE -> "Started by schedule " + scheduleId;
            case WEBHOOK, KAFKA -> "Started by " + triggeredBy;
        });
        return run;
    }

    private StepRun newStep(FlowRun run, int index, FlowStep step, Instant now) {
        int attempts = step.maxAttempts() == null ? properties.maxAttempts() : step.maxAttempts();
        Duration timeout = step.timeoutSeconds() != null ? Duration.ofSeconds(step.timeoutSeconds())
                : step.lane() == Lane.HEAVY ? properties.reportTimeout() : properties.lightTimeout();
        return new StepRun(run, index, step, attempts, timeout, now);
    }

    // ---- executing ------------------------------------------------------------------------------------

    /**
     * Claims up to {@code limit} READY steps of a lane for this instance, in one transaction. The steps and their runs
     * are locked with {@code SKIP LOCKED}: instances polling at the same moment get disjoint steps, nobody waits.
     *
     * @param instanceId recorded in the history (which instance ran which attempt)
     * @param tokens     a new claim token per step
     */
    @Transactional
    List<ClaimedStep> claim(Lane lane, int limit, String instanceId, Supplier<String> tokens, Instant now,
                            Instant leaseUntil) {
        List<ClaimedStep> claimed = new ArrayList<>();
        for (Long stepId : steps.lockReady(lane.name(), now, limit)) {
            StepRun step = steps.findById(stepId).orElseThrow();
            FlowRun run = runs.findById(step.getRunId()).orElseThrow();   // locked by lockReady as well
            String token = tokens.get();
            step.claim(token, now, leaseUntil);
            run.started(now);
            FlowStep definition;
            try {
                definition = run.definition().steps().get(step.getStepIndex());
            } catch (RuntimeException e) {
                // a stored definition this version cannot read: fail the run instead of retrying it forever
                applyFailure(run, step, StepFailure.permanent("INVALID_FLOW_DEFINITION",
                        "The stored flow definition cannot be read: " + e.getMessage()), now);
                continue;
            }
            event(run.getId(), now, "step_started", step.getStepKey(),
                    "Attempt " + step.getAttempt() + " of " + step.getMaxAttempts() + " on " + instanceId);
            // outputs of the steps that ran before this one on the path the run took
            Map<String, Map<String, Object>> outputs = new LinkedHashMap<>();
            for (StepRun earlier : steps.findByRunIdOrderById(run.getId())) {
                if (earlier.getStatus() == StepStatus.COMPLETED && !earlier.getId().equals(step.getId())) {
                    outputs.put(earlier.getStepKey(), FlowDefinition.outputFromJson(earlier.getOutputJson()));
                }
            }
            claimed.add(new ClaimedStep(step.getId(), token, step.getAttempt(), run.getId(), run.getFlowId(),
                    run.getFlowName(), run.getRunAs(), run.getTriggerType(), run.getTriggeredBy(), run.getStartedAt(),
                    definition, Duration.ofSeconds(step.getTimeoutSeconds()), outputs,
                    FlowDefinition.valueFromJson(run.getTriggerPayloadJson())));
        }
        return claimed;
    }

    @Transactional
    int renew(long stepId, String token, Instant leaseUntil) {
        return steps.renew(stepId, token, leaseUntil);
    }

    /**
     * Records a successful attempt and queues the next step (or finishes the run).
     *
     * @return false if this attempt no longer owned the step (its lease expired and it was taken over)
     */
    @Transactional
    boolean complete(long stepId, String token, StepResult result) {
        Locked locked = lockStep(stepId);
        if (locked == null || !locked.step().isClaimedBy(token)) {
            return false;
        }
        Instant now = clock.instant();
        FlowRun run = locked.run();
        StepRun step = locked.step();
        if (result.waitUntil() != null && !run.isCancelRequested()) {
            step.waiting(FlowDefinition.toJson(result.output()), result.waitUntil());
            event(run.getId(), now, "step_waiting", step.getStepKey(), "Waiting until " + result.waitUntil());
            return true;
        }
        advance(run, step, result.output(), result.outcome(), now);
        return true;
    }

    /** Completes {@code step} with its outcome and queues the step the graph leads to, or finishes the run. */
    private void advance(FlowRun run, StepRun step, Map<String, Object> output, String outcome, Instant now) {
        step.completed(now, FlowDefinition.toJson(output), outcome);
        event(run.getId(), now, "step_completed", step.getStepKey(), outcome == null ? null : "Outcome: " + outcome);
        if (run.isCancelRequested()) {
            run.cancelled(now);
            event(run.getId(), now, "run_cancelled", null, null);
            return;
        }
        FlowGraph graph = new FlowGraph(run.definition());
        Optional<FlowStep> next = graph.next(step.getStepKey(), outcome);
        if (next.isPresent()) {
            steps.save(newStep(run, graph.indexOf(next.get().key()), next.get(), now));
        } else {
            run.completed(now);
            event(run.getId(), now, "run_completed", null, outcome == null ? null
                    : "Ended after '" + step.getStepKey() + "' (" + outcome + ")");
        }
    }

    // ---- approvals ------------------------------------------------------------------------------------

    /**
     * Records a decision on an approval request and lets the run go on. Only members of the request's group may
     * decide, and not the person who started the run or owns the flow unless the step allows it.
     *
     * @param memberOf the groups the deciding user belongs to
     */
    @Transactional
    ApprovalRequest decide(long requestId, boolean approve, String comment, String user, Set<Long> memberOf) {
        Long runId = approvals.findRunId(requestId).orElseThrow(() -> FlowException.notFound(
                "APPROVAL_NOT_FOUND", "Approval request " + requestId + " does not exist."));
        FlowRun run = runs.lock(runId).orElseThrow();
        ApprovalRequest request = approvals.findById(requestId).orElseThrow();   // read after the lock: current
        if (!memberOf.contains(request.getGroupId())) {
            throw new FlowException(Kind.FORBIDDEN, "NOT_APPROVER",
                    "Only members of '" + request.getGroupName() + "' may decide this request.");
        }
        if (request.getStatus() != ApprovalRequest.Status.PENDING) {
            throw new FlowException(Kind.CONFLICT, "APPROVAL_ALREADY_DECIDED",
                    "The request is already " + request.getStatus().name().toLowerCase() + ".");
        }
        if (!request.isAllowSelfApproval() && request.isRequestedBy(user)) {
            throw new FlowException(Kind.FORBIDDEN, "SELF_APPROVAL_FORBIDDEN",
                    "You started this run or own the flow; someone else from the group has to decide.");
        }
        StepRun step = steps.findById(request.getStepRunId()).orElseThrow();
        if (step.getStatus() != StepStatus.WAITING) {
            throw new FlowException(Kind.CONFLICT, "APPROVAL_NOT_READY", "The step is not waiting yet; try again.");
        }
        Instant now = clock.instant();
        request.decide(approve, user, comment, now);
        Map<String, Object> output = new LinkedHashMap<>(FlowDefinition.outputFromJson(step.getOutputJson()));
        output.put("decision", approve ? "approved" : "rejected");
        output.put("decidedBy", user);
        output.put("comment", comment);
        output.put("decidedAt", now.toString());
        event(run.getId(), now, approve ? "approval_approved" : "approval_rejected", step.getStepKey(),
                user + (comment == null || comment.isBlank() ? "" : ": " + comment));
        advance(run, step, output, approve ? "approved" : "rejected", now);
        return request;
    }

    /** Approvals nobody decided in time take their {@code timeout} way; instances share the work (SKIP LOCKED). */
    @Transactional
    int timeOutWaitingSteps(Instant now, int limit) {
        List<Long> due = steps.lockDueWaiting(now, limit);
        for (Long stepId : due) {
            StepRun step = steps.findById(stepId).orElseThrow();
            FlowRun run = runs.findById(step.getRunId()).orElseThrow();
            approvals.findByStepRunId(stepId).ifPresent(request -> request.timedOut(now));
            Map<String, Object> output = new LinkedHashMap<>(FlowDefinition.outputFromJson(step.getOutputJson()));
            output.put("decision", "timeout");
            event(run.getId(), now, "approval_timed_out", step.getStepKey(), null);
            advance(run, step, output, "timeout", now);
        }
        return due.size();
    }

    @Transactional
    void fail(long stepId, String token, StepFailure failure) {
        Locked locked = lockStep(stepId);
        if (locked != null && locked.step().isClaimedBy(token)) {
            applyFailure(locked.run(), locked.step(), failure, clock.instant());
        }
    }

    /**
     * Steps whose instance stopped renewing the lease (died, lost the database, paused too long): the attempt counts
     * as failed and is retried. Locked with {@code SKIP LOCKED}, so instances recover different steps.
     *
     * @return how many steps were recovered
     */
    @Transactional
    int recoverExpiredLeases(Instant now, int limit) {
        List<Long> expired = steps.lockExpiredLeases(now, limit);
        for (Long stepId : expired) {
            StepRun step = steps.findById(stepId).orElseThrow();
            FlowRun run = runs.findById(step.getRunId()).orElseThrow();
            applyFailure(run, step, StepFailure.retryable("STEP_LEASE_EXPIRED",
                    "The instance running the step stopped responding"), now);
        }
        return expired.size();
    }

    private void applyFailure(FlowRun run, StepRun step, StepFailure failure, Instant now) {
        String key = step.getStepKey();
        if (failure.cancelled() || run.isCancelRequested()) {
            step.cancelled(now);
            run.cancelled(now);
            event(run.getId(), now, "step_cancelled", key, null);
            event(run.getId(), now, "run_cancelled", null, null);
            return;
        }
        if (failure.release()) {
            step.release(now);
            event(run.getId(), now, "step_released", key, failure.message());
            return;
        }
        if (failure.retryable() && step.getAttempt() < step.getMaxAttempts()) {
            Duration delay = backoff(step.getAttempt());
            step.retryAt(now.plus(delay), failure.code(), failure.message());
            event(run.getId(), now, "step_retry_scheduled", key, "Attempt " + step.getAttempt() + " failed ("
                    + failure.code() + "): " + failure.message() + ". Next attempt in " + delay.toSeconds() + "s");
            return;
        }
        step.failed(now, failure.code(), failure.message());
        run.failed(now, failure.code(), "Step '" + key + "' failed: " + failure.message());
        event(run.getId(), now, "step_failed", key, failure.code() + ": " + failure.message());
        event(run.getId(), now, "run_failed", null, null);
    }

    private Duration backoff(int failedAttempt) {
        Duration delay = properties.retryBackoff().multipliedBy(1L << Math.min(failedAttempt - 1, 16));
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    // ---- cancelling -----------------------------------------------------------------------------------

    /**
     * Steps that wait are cancelled at once; a running step is stopped by its instance (heartbeat), which then
     * finishes the cancellation. Cancelling twice is harmless.
     */
    @Transactional
    FlowRun cancel(long runId, String by) {
        FlowRun run = runs.lock(runId).orElseThrow(() -> FlowException.notFound("RUN_NOT_FOUND",
                "Run " + runId + " does not exist."));
        if (run.getStatus().isFinished()) {
            throw new FlowException(Kind.CONFLICT, "RUN_ALREADY_FINISHED",
                    "The run has already finished (" + run.getStatus().name().toLowerCase() + ").");
        }
        Instant now = clock.instant();
        if (!run.isCancelRequested()) {
            run.requestCancel();
            event(runId, now, "cancel_requested", null, "Cancelled by " + by);
        }
        for (StepStatus idle : List.of(StepStatus.READY, StepStatus.WAITING)) {
            for (StepRun waiting : steps.findByRunIdAndStatus(runId, idle)) {
                waiting.cancelled(now);
                event(runId, now, "step_cancelled", waiting.getStepKey(), null);
            }
        }
        approvals.findByRunIdAndStatus(runId, ApprovalRequest.Status.PENDING).forEach(request -> request.cancelled(now));
        if (steps.findByRunIdAndStatus(runId, StepStatus.RUNNING).isEmpty()) {
            run.cancelled(now);
            event(runId, now, "run_cancelled", null, null);
        }
        return run;
    }

    // ---- helpers --------------------------------------------------------------------------------------

    private record Locked(FlowRun run, StepRun step) {
    }

    /** Locks the step's run first; the step is read after the lock, so it is current. */
    private Locked lockStep(long stepId) {
        Long runId = steps.findRunId(stepId).orElse(null);
        if (runId == null) {
            return null;
        }
        FlowRun run = runs.lock(runId).orElse(null);
        StepRun step = steps.findById(stepId).orElse(null);
        return run == null || step == null ? null : new Locked(run, step);
    }

    private void event(Long runId, Instant at, String type, String stepKey, String message) {
        events.save(new RunEvent(runId, at, type, stepKey, message));
    }
}
