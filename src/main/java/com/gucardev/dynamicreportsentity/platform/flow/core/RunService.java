package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.core.FlowException.Kind;
import com.gucardev.dynamicreportsentity.platform.reporting.ReportViewer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;

import java.util.Collection;
import java.util.List;

/**
 * Starting, cancelling and reading runs. A run is visible to the flow owner (it runs as them), to the user who
 * started it and to report admins.
 */
@Service
public class RunService {

    private final FlowService flows;
    private final RunStore store;
    private final FlowRunRepository runs;
    private final StepRunRepository steps;
    private final RunEventRepository events;
    private final ArtifactStore artifacts;
    private final FlowEngine engine;
    private final Clock clock;

    RunService(FlowService flows, RunStore store, FlowRunRepository runs, StepRunRepository steps,
               RunEventRepository events, ArtifactStore artifacts, FlowEngine engine, Clock clock) {
        this.flows = flows;
        this.store = store;
        this.runs = runs;
        this.steps = steps;
        this.events = events;
        this.artifacts = artifacts;
        this.engine = engine;
        this.clock = clock;
    }

    /**
     * Queues a run (answers at once); the engine picks it up on some instance. There is no per-user limit: runs
     * wait in the queue for a free slot, so many runs only take longer, they never overload the system.
     */
    public FlowRun start(long flowId, ReportViewer user) {
        Flow flow = flows.get(flowId, user);
        FlowRun run = store.createRun(flow, TriggerType.MANUAL, user.username(), null, RunStore.PRIORITY_MANUAL);
        engine.wakeUp();
        return run;
    }

    public FlowRun cancel(long runId, ReportViewer user) {
        get(runId, user);
        FlowRun run = store.cancel(runId, user.username());
        engine.cancelLocal(runId);
        return run;
    }

    public FlowRun get(long runId, ReportViewer user) {
        FlowRun run = runs.findById(runId).orElseThrow(() -> notFound(runId));
        if (!canSee(run, user)) {
            throw notFound(runId);
        }
        return run;
    }

    public List<StepRun> steps(long runId) {
        return steps.findByRunIdOrderById(runId);
    }

    public List<RunEvent> events(long runId) {
        return events.findByRunIdOrderById(runId);
    }

    /** Newest first; {@code flowId} null = runs of every visible flow. */
    public Page<FlowRun> list(Long flowId, Collection<RunStatus> statuses, ReportViewer user, int page, int size) {
        if (flowId != null) {
            flows.get(flowId, user);
        }
        String runAs = user.hasAuthority(FlowService.ADMIN_AUTHORITY) || flowId != null ? null : user.username();
        return runs.search(runAs, flowId, statuses.isEmpty() ? List.of(RunStatus.values()) : statuses,
                PageRequest.of(page - 1, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    }

    public List<RunArtifact> files(long runId) {
        return artifacts.forRun(runId);
    }

    /** A file of a visible run; 404 if not visible, 410 once it has expired. */
    public RunArtifact file(long artifactId, ReportViewer user) {
        RunArtifact artifact = artifacts.find(artifactId).orElseThrow(() -> artifactNotFound(artifactId));
        try {
            get(artifact.getRunId(), user);
        } catch (FlowException e) {
            throw artifactNotFound(artifactId);
        }
        if (!artifact.isAvailable(clock.instant())) {
            throw new FlowException(Kind.GONE, "FILE_EXPIRED", "The file was kept until "
                    + artifact.getExpiresAt() + " and has been deleted (flows.file-retention).");
        }
        return artifact;
    }

    static boolean canSee(FlowRun run, ReportViewer user) {
        return run.getRunAs().equals(user.username()) || run.getTriggeredBy().equals(user.username())
                || user.hasAuthority(FlowService.ADMIN_AUTHORITY);
    }

    private static FlowException notFound(long runId) {
        return FlowException.notFound("RUN_NOT_FOUND", "Run " + runId + " does not exist.");
    }

    private static FlowException artifactNotFound(long id) {
        return FlowException.notFound("ARTIFACT_NOT_FOUND", "File " + id + " does not exist.");
    }
}
