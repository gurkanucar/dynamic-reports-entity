package com.gucardev.dynamicreportsentity.platform.flow.core;

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
    private final FlowProperties properties;
    private final Clock clock;

    RunStore(FlowRunRepository runs, StepRunRepository steps, RunEventRepository events,
             FlowScheduleRepository schedules, FlowRepository flows, FlowProperties properties, Clock clock) {
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
        return newRun(flow, trigger, triggeredBy, scheduleId, priority, clock.instant());
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
                PRIORITY_SCHEDULE, now);
        schedule.fired(fireTime, next, run.getId(), null);
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
                           Instant now) {
        FlowRun run = runs.save(new FlowRun(flow, trigger, triggeredBy, scheduleId, priority, now));
        steps.save(newStep(run, 0, run.definition().steps().getFirst(), now));
        event(run.getId(), now, "run_queued", null, trigger == TriggerType.MANUAL
                ? "Started by " + triggeredBy : "Started by schedule " + scheduleId);
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
            Map<String, Map<String, Object>> outputs = new LinkedHashMap<>();
            for (StepRun earlier : steps.findByRunIdOrderByStepIndex(run.getId())) {
                if (earlier.getStepIndex() < step.getStepIndex() && earlier.getStatus() == StepStatus.COMPLETED) {
                    outputs.put(earlier.getStepKey(), FlowDefinition.outputFromJson(earlier.getOutputJson()));
                }
            }
            claimed.add(new ClaimedStep(step.getId(), token, step.getAttempt(), run.getId(), run.getFlowId(),
                    run.getFlowName(), run.getRunAs(), run.getTriggerType(), run.getTriggeredBy(), run.getStartedAt(),
                    definition, Duration.ofSeconds(step.getTimeoutSeconds()), outputs));
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
    boolean complete(long stepId, String token, Map<String, Object> output) {
        Locked locked = lockStep(stepId);
        if (locked == null || !locked.step().isClaimedBy(token)) {
            return false;
        }
        Instant now = clock.instant();
        FlowRun run = locked.run();
        StepRun step = locked.step();
        step.completed(now, FlowDefinition.toJson(output));
        event(run.getId(), now, "step_completed", step.getStepKey(), null);

        if (run.isCancelRequested()) {
            run.cancelled(now);
            event(run.getId(), now, "run_cancelled", null, null);
            return true;
        }
        List<FlowStep> definitions = run.definition().steps();
        int next = step.getStepIndex() + 1;
        if (next < definitions.size()) {
            steps.save(newStep(run, next, definitions.get(next), now));
        } else {
            run.completed(now);
            event(run.getId(), now, "run_completed", null, null);
        }
        return true;
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
        for (StepRun waiting : steps.findByRunIdAndStatus(runId, StepStatus.READY)) {
            waiting.cancelled(now);
            event(runId, now, "step_cancelled", waiting.getStepKey(), null);
        }
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
