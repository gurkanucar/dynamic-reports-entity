package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.reporting.ReportAccessDeniedException;
import com.gucardev.dynamicreportsentity.platform.reporting.query.InvalidReportException;
import com.gucardev.dynamicreportsentity.platform.reporting.query.QueryCancelledException;
import com.gucardev.dynamicreportsentity.platform.reporting.query.QueryHandle;
import jakarta.mail.SendFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.Limit;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs flow steps on every app instance, with the database as the only coordinator.
 * <ul>
 *   <li><b>Pull, never push.</b> Each instance has a fixed number of slots per {@link Lane} and only claims as
 *       many READY steps as it has free slots. A thousand runs started at once simply wait in the table;
 *       nothing piles up in memory. More instances = more slots.</li>
 *   <li><b>Claims and leases.</b> A claim ({@link RunStore#claim}) takes ready steps with
 *       {@code FOR UPDATE SKIP LOCKED}: concurrent instances get different steps without waiting for each other.
 *       A claimed step belongs to its instance under a lease, renewed by the heartbeat; a step whose lease expires
 *       (instance died) is retried by any instance.</li>
 *   <li><b>Cancel and timeout.</b> The heartbeat stops local steps whose run was cancelled or whose time is up:
 *       queries through {@link QueryHandle} ({@code Statement.cancel()}), other work by interrupting it.</li>
 *   <li><b>Retries</b> with exponential backoff for errors another attempt may fix (database, mail server,
 *       timeouts); invalid definitions or missing permissions fail at once.</li>
 *   <li><b>Shutdown</b> hands running steps back without counting the attempt, so rolling deployments do not
 *       burn attempts.</li>
 * </ul>
 * Latency: a start or finish on this instance triggers a poll right away; other instances notice within
 * {@code flows.poll-interval}.
 */
@Slf4j
@Component
public class FlowEngine implements SmartLifecycle {

    private static final int RECOVER_BATCH = 50;
    private static final int CLEANUP_BATCH = 100;
    private static final int CLEANUP_ROUNDS = 50;

    private final RunStore store;
    private final FlowRunRepository runs;
    private final FlowScheduleRepository schedules;
    private final ArtifactStore artifacts;
    private final Map<String, StepHandler> handlers;
    private final FlowProperties properties;
    private final Clock clock;
    private final String instanceId;
    private final AtomicLong claims = new AtomicLong();

    private final Map<Lane, Semaphore> slots = new EnumMap<>(Lane.class);
    private final Map<Long, Execution> executions = new ConcurrentHashMap<>();
    private final ReentrantLock pollLock = new ReentrantLock();
    private final AtomicBoolean pollAgain = new AtomicBoolean();

    private volatile boolean running;
    private ScheduledExecutorService timers;
    private ExecutorService workers;

    @Autowired
    FlowEngine(RunStore store, FlowRunRepository runs, FlowScheduleRepository schedules, ArtifactStore artifacts,
               List<StepHandler> handlers, FlowProperties properties, Clock clock) {
        this(store, runs, schedules, artifacts, handlers, properties, clock, defaultInstanceId());
    }

    /** Tests create more engines on the same database, each with its own id, to play more instances. */
    FlowEngine(RunStore store, FlowRunRepository runs, FlowScheduleRepository schedules, ArtifactStore artifacts,
               List<StepHandler> handlers, FlowProperties properties, Clock clock, String instanceId) {
        this.store = store;
        this.runs = runs;
        this.schedules = schedules;
        this.artifacts = artifacts;
        this.instanceId = instanceId;
        this.handlers = handlers.stream().collect(Collectors.toMap(StepHandler::type, Function.identity()));
        this.properties = properties;
        this.clock = clock;
        slots.put(Lane.HEAVY, new Semaphore(properties.heavySlots()));
        slots.put(Lane.LIGHT, new Semaphore(properties.lightSlots()));
    }

    private static String defaultInstanceId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            host = "unknown-host";
        }
        return host + "/" + ProcessHandle.current().pid() + "/" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ---- lifecycle ------------------------------------------------------------------------------------

    @Override
    public synchronized void start() {
        workers = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("flow-step-", 1).factory());
        timers = Executors.newScheduledThreadPool(4, Thread.ofPlatform().name("flow-engine-", 1).daemon().factory());
        running = true;
        long poll = properties.pollInterval().toMillis();
        long beat = heartbeatInterval().toMillis();
        long schedule = properties.scheduleInterval().toMillis();
        long cleanup = properties.cleanupInterval().toMillis();
        timers.scheduleWithFixedDelay(() -> safely("poll", this::poll), poll, poll, TimeUnit.MILLISECONDS);
        timers.scheduleWithFixedDelay(() -> safely("heartbeat", this::heartbeat), beat, beat, TimeUnit.MILLISECONDS);
        timers.scheduleWithFixedDelay(() -> safely("schedules", this::fireDueSchedules), schedule, schedule,
                TimeUnit.MILLISECONDS);
        timers.scheduleWithFixedDelay(() -> safely("file cleanup", this::deleteExpiredFiles), cleanup, cleanup,
                TimeUnit.MILLISECONDS);
        log.info("Flow engine started on {} (heavy slots {}, light slots {})", instanceId,
                properties.heavySlots(), properties.lightSlots());
    }

    /** Stops polling, hands running steps back (not counted as attempts) and waits for them a little. */
    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        timers.shutdownNow();
        executions.values().forEach(execution -> execution.stop(StopReason.SHUTDOWN));
        workers.shutdown();
        try {
            if (!workers.awaitTermination(properties.shutdownGrace().toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("{} flow steps did not stop in time; their leases will expire and they will be retried",
                        executions.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private Duration heartbeatInterval() {
        Duration quarter = properties.lease().dividedBy(4);
        Duration max = Duration.ofSeconds(5);   // also the delay of a cancel requested on another instance
        return quarter.compareTo(max) > 0 ? max : quarter;
    }

    // ---- polling --------------------------------------------------------------------------------------

    /** Asks for a poll as soon as possible (after a local start or finish). */
    public void wakeUp() {
        if (running) {
            try {
                timers.execute(() -> safely("poll", this::poll));
            } catch (RejectedExecutionException ignored) {
                // stopping
            }
        }
    }

    /** Recovers expired leases, then claims ready steps for the free slots of each lane. */
    public void poll() {
        if (!pollLock.tryLock()) {
            pollAgain.set(true);   // the running poll goes once more
            return;
        }
        try {
            do {
                pollAgain.set(false);
                pollOnce();
            } while (pollAgain.get() && running);
        } finally {
            pollLock.unlock();
        }
    }

    private void pollOnce() {
        Instant now = clock.instant();
        store.recoverExpiredLeases(now, RECOVER_BATCH);
        store.timeOutWaitingSteps(now, RECOVER_BATCH);
        for (Lane lane : Lane.values()) {
            Semaphore laneSlots = slots.get(lane);
            int free = laneSlots.availablePermits();   // only this (locked) poll takes permits; finishing steps add
            if (free <= 0 || !running) {
                continue;
            }
            List<ClaimedStep> claimed = store.claim(lane, free, instanceId,
                    () -> instanceId + "#" + claims.incrementAndGet(), now, now.plus(properties.lease()));
            for (ClaimedStep step : claimed) {
                laneSlots.acquireUninterruptibly();
                launch(step, laneSlots);
            }
        }
    }

    private void launch(ClaimedStep claimed, Semaphore laneSlots) {
        StepHandler handler = handlers.get(claimed.step().typeName());
        if (handler == null) {   // e.g. a Kafka step while Kafka is switched off on this installation
            laneSlots.release();
            store.fail(claimed.stepRunId(), claimed.token(), StepFailure.permanent("STEP_TYPE_UNAVAILABLE",
                    "Steps of type '" + claimed.step().typeName() + "' cannot run here (switched off)"));
            return;
        }
        Execution execution = new Execution(claimed, handler.interruptible(), clock.instant().plus(claimed.timeout()));
        executions.put(claimed.stepRunId(), execution);
        try {
            workers.execute(() -> {
                try {
                    execute(execution, handler);
                } finally {
                    executions.remove(claimed.stepRunId());
                    laneSlots.release();
                    wakeUp();
                }
            });
        } catch (RejectedExecutionException e) {
            executions.remove(claimed.stepRunId());
            laneSlots.release();
            store.fail(claimed.stepRunId(), claimed.token(), StepFailure.handBack());
        }
    }

    private void execute(Execution execution, StepHandler handler) {
        ClaimedStep claimed = execution.claimed;
        execution.begin();
        try {
            StepResult result = handler.execute(
                    new StepHandler.StepContext(claimed, execution.queryHandle, execution.deadline));
            execution.end();
            if (!store.complete(claimed.stepRunId(), claimed.token(), result)) {
                log.warn("Flow run {} step '{}' finished after its lease was taken over; result dropped",
                        claimed.runId(), claimed.step().key());
            }
        } catch (Throwable e) {
            execution.end();
            StepFailure failure = classify(e, execution);
            if (failure.code().equals("INTERNAL_ERROR") || failure.code().equals("DATABASE_ERROR")) {
                log.error("Flow run {} step '{}' attempt {} failed", claimed.runId(), claimed.step().key(),
                        claimed.attempt(), e);
            } else {
                log.info("Flow run {} step '{}' attempt {} ended: {} {}", claimed.runId(), claimed.step().key(),
                        claimed.attempt(), failure.code(), failure.message());
            }
            try {
                store.fail(claimed.stepRunId(), claimed.token(), failure);
            } catch (RuntimeException storeFailure) {
                log.warn("Could not record the failure of flow step {}; its lease will expire and it will be "
                        + "retried: {}", claimed.stepRunId(), storeFailure.getMessage());
            }
        }
    }

    StepFailure classify(Throwable error, Execution execution) {
        StopReason reason = execution.stopReason;
        if (reason == StopReason.CANCELLED) {
            return StepFailure.cancel();
        }
        if (reason == StopReason.SHUTDOWN) {
            return StepFailure.handBack();
        }
        if (reason == StopReason.TIMEOUT || error instanceof QueryTimeoutException) {
            return StepFailure.retryable("STEP_TIMEOUT", "The step did not finish within "
                    + execution.claimed.timeout().toSeconds() + "s");
        }
        if (reason == StopReason.LEASE_LOST) {
            return StepFailure.retryable("STEP_LEASE_EXPIRED", "The lease of the step was taken over");
        }
        return switch (error) {
            case StepException known -> new StepFailure(known.code(), known.getMessage(), known.retryable(), false,
                    false);
            case InvalidReportException invalid -> StepFailure.permanent("INVALID_REPORT_DEFINITION",
                    invalid.getMessage());
            case ReportAccessDeniedException denied -> StepFailure.permanent("REPORT_ACCESS_DENIED",
                    denied.getMessage());
            case QueryCancelledException cancelled -> StepFailure.retryable("STEP_INTERRUPTED",
                    "The query was stopped");
            case MailAuthenticationException auth -> StepFailure.retryable("MAIL_SERVER_UNAVAILABLE",
                    "The mail server rejected the login");
            case MailSendException send when rejectsRecipients(send) -> StepFailure.permanent(
                    "MAIL_RECIPIENT_REJECTED", "The mail server rejected a recipient: " + send.getMessage());
            case MailSendException send -> StepFailure.retryable("MAIL_SERVER_UNAVAILABLE",
                    "The e-mail could not be sent: " + send.getMessage());
            case MailException mail -> StepFailure.permanent("MAIL_REJECTED",
                    "The e-mail could not be built: " + mail.getMessage());
            case DataAccessException database -> StepFailure.retryable("DATABASE_ERROR",
                    "Database error (details in the server log)");
            case InterruptedException interrupted -> StepFailure.retryable("STEP_INTERRUPTED",
                    "The step was interrupted");
            default -> StepFailure.retryable("INTERNAL_ERROR", "Unexpected error (details in the server log)");
        };
    }

    private static boolean rejectsRecipients(MailSendException exception) {
        List<Throwable> roots = new java.util.ArrayList<>(List.of(exception));
        roots.addAll(exception.getFailedMessages().values());
        for (Throwable root : roots) {
            for (Throwable cause = root; cause != null; cause = cause.getCause()) {
                if (cause instanceof SendFailedException failed && failed.getInvalidAddresses() != null
                        && failed.getInvalidAddresses().length > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- heartbeat ------------------------------------------------------------------------------------

    /** Renews the leases of local steps and stops those that were cancelled, timed out or taken over. */
    void heartbeat() {
        if (executions.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        Instant until = now.plus(properties.lease());
        for (Execution execution : executions.values()) {
            try {
                if (store.renew(execution.claimed.stepRunId(), execution.claimed.token(), until) == 0) {
                    execution.stop(StopReason.LEASE_LOST);
                }
            } catch (RuntimeException e) {
                log.warn("Could not renew the lease of flow step {}: {}", execution.claimed.stepRunId(),
                        e.getMessage());
            }
        }
        Set<Long> runIds = executions.values().stream().map(execution -> execution.claimed.runId())
                .collect(Collectors.toSet());
        Set<Long> cancelled = runIds.isEmpty() ? Set.of() : runs.findCancelRequested(runIds);
        for (Execution execution : executions.values()) {
            if (cancelled.contains(execution.claimed.runId())) {
                execution.stop(StopReason.CANCELLED);
            } else if (now.isAfter(execution.deadline)) {
                execution.stop(StopReason.TIMEOUT);
            }
        }
    }

    /** A cancel on this instance stops its local steps right away (other instances: next heartbeat). */
    void cancelLocal(long runId) {
        executions.values().stream()
                .filter(execution -> execution.claimed.runId() == runId)
                .forEach(execution -> execution.stop(StopReason.CANCELLED));
    }

    // ---- schedules ------------------------------------------------------------------------------------

    /** Starts the runs of due schedules; every instance does this, the schedule row lock decides. */
    public void fireDueSchedules() {
        Instant now = clock.instant();
        boolean started = false;
        for (Long scheduleId : schedules.findDueIds(now, Limit.of(100))) {
            try {
                started |= store.fireSchedule(scheduleId, now).isPresent();
            } catch (RuntimeException e) {
                log.warn("Could not fire flow schedule {}: {}", scheduleId, e.getMessage());
                try {
                    store.skipBrokenSchedule(scheduleId, now, e.getMessage());
                } catch (RuntimeException ignored) {
                    // tried again next round
                }
            }
        }
        if (started) {
            wakeUp();
        }
    }

    // ---- file retention -------------------------------------------------------------------------------

    /** Deletes files past {@code flows.file-retention}; instances share the work through {@code SKIP LOCKED}. */
    public void deleteExpiredFiles() {
        for (int round = 0; round < CLEANUP_ROUNDS && artifacts.deleteExpired(CLEANUP_BATCH) == CLEANUP_BATCH; round++) {
            // next batch
        }
    }

    private void safely(String task, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Flow engine {} failed: {}", task, e.getMessage());
        }
    }

    // ---- local executions -----------------------------------------------------------------------------

    enum StopReason { CANCELLED, TIMEOUT, SHUTDOWN, LEASE_LOST }

    /** A step running on this instance. */
    static final class Execution {

        final ClaimedStep claimed;
        final QueryHandle queryHandle = new QueryHandle();
        final Instant deadline;
        private final boolean interruptible;
        volatile StopReason stopReason;
        private Thread thread;

        Execution(ClaimedStep claimed, boolean interruptible, Instant deadline) {
            this.claimed = claimed;
            this.interruptible = interruptible;
            this.deadline = deadline;
        }

        synchronized void begin() {
            thread = Thread.currentThread();
        }

        /** After this, the thread is no longer interrupted (it goes on to write the result). */
        synchronized void end() {
            thread = null;
            Thread.interrupted();
        }

        synchronized void stop(StopReason reason) {
            if (stopReason == null) {
                stopReason = reason;
            }
            queryHandle.cancel();
            if (interruptible && thread != null) {
                thread.interrupt();
            }
        }
    }
}
