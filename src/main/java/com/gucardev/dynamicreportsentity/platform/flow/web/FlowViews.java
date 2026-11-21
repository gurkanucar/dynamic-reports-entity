package com.gucardev.dynamicreportsentity.platform.flow.web;

import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowStep;
import com.gucardev.dynamicreportsentity.platform.flow.core.Flow;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowDefinition;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowEdge;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowTrigger;
import com.gucardev.dynamicreportsentity.platform.flow.core.StepStatus;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowRun;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowSchedule;
import com.gucardev.dynamicreportsentity.platform.flow.core.RunArtifact;
import com.gucardev.dynamicreportsentity.platform.flow.core.RunEvent;
import com.gucardev.dynamicreportsentity.platform.flow.core.StepRun;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.gucardev.dynamicreportsentity.platform.flow.web.WebSupport.time;
import static com.gucardev.dynamicreportsentity.platform.flow.web.WebSupport.value;

/**
 * Response bodies of the flow API. Every field is always present ({@code null} when empty); lists are never
 * null; enums are snake_case; times are ISO 8601 UTC.
 */
final class FlowViews {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private FlowViews() {
    }

    record UserRef(String username) {
    }

    record FlowRef(long id, String name) {
    }

    record ErrorInfo(String code, String message) {
        static ErrorInfo of(String code, String message) {
            return code == null ? null : new ErrorInfo(code, message);
        }
    }

    // ---- flows ----------------------------------------------------------------------------------------

    record FlowSummary(long id, String name, String description, UserRef owner, int stepCount, long version,
                       String createdAt, String updatedAt) {
    }

    record FlowDetail(long id, String name, String description, UserRef owner, List<FlowStep> steps,
                      List<FlowEdge> edges, Map<String, FlowDefinition.Position> layout, long version,
                      String createdAt, String updatedAt, UserRef updatedBy) {
    }

    static FlowSummary summary(Flow flow) {
        return new FlowSummary(flow.getId(), flow.getName(), flow.getDescription(), new UserRef(flow.getOwner()),
                flow.definition().steps().size(), flow.getVersion(), time(flow.getCreatedAt()),
                time(flow.getUpdatedAt()));
    }

    static FlowDetail detail(Flow flow) {
        FlowDefinition definition = flow.definition();
        return new FlowDetail(flow.getId(), flow.getName(), flow.getDescription(), new UserRef(flow.getOwner()),
                definition.steps(), definition.edges(), definition.layout(), flow.getVersion(),
                time(flow.getCreatedAt()), time(flow.getUpdatedAt()), new UserRef(flow.getUpdatedBy()));
    }

    // ---- triggers -------------------------------------------------------------------------------------

    /** @param webhookUrl path to call (WEBHOOK); the secret is only shown when created or rotated */
    record TriggerView(long id, long flowId, String type, boolean enabled, String topic, Object filter,
                       String webhookUrl, String lastFiredAt, Long lastRunId, long version, UserRef createdBy,
                       String createdAt, String updatedAt) {
    }

    record TriggerWithSecret(TriggerView trigger, String secret) {
    }

    static TriggerView trigger(FlowTrigger trigger) {
        return new TriggerView(trigger.getId(), trigger.getFlowId(), value(trigger.getType()), trigger.isEnabled(),
                trigger.getTopic(), trigger.getFilterJson() == null ? null : JSON.readValue(trigger.getFilterJson(), Object.class),
                trigger.getWebhookToken() == null ? null : "/api/flow-hooks/" + trigger.getWebhookToken(),
                time(trigger.getLastFiredAt()), trigger.getLastRunId(), trigger.getVersion(),
                new UserRef(trigger.getCreatedBy()), time(trigger.getCreatedAt()), time(trigger.getUpdatedAt()));
    }

    // ---- schedules ------------------------------------------------------------------------------------

    record ScheduleView(long id, long flowId, String cron, String timeZone, boolean enabled, String nextFireAt,
                        String lastFiredAt, Long lastRunId, String lastSkipReason, long version, UserRef createdBy,
                        String createdAt, String updatedAt) {
    }

    static ScheduleView schedule(FlowSchedule schedule) {
        return new ScheduleView(schedule.getId(), schedule.getFlowId(), schedule.getCron(), schedule.getTimeZone(),
                schedule.isEnabled(), time(schedule.getNextFireAt()), time(schedule.getLastFiredAt()),
                schedule.getLastRunId(), schedule.getLastSkipReason(), schedule.getVersion(),
                new UserRef(schedule.getCreatedBy()), time(schedule.getCreatedAt()), time(schedule.getUpdatedAt()));
    }

    // ---- runs -----------------------------------------------------------------------------------------

    record RunSummary(long id, FlowRef flow, String status, boolean cancelRequested, String trigger,
                      String triggeredBy, UserRef runAs, Long scheduleId, String createdAt, String startedAt,
                      String finishedAt, ErrorInfo error) {
    }

    /** @param downloadUrl null once the file has expired */
    record FileView(long id, String stepKey, String fileName, long sizeBytes, String expiresAt, boolean expired,
                    String downloadUrl) {
    }

    /**
     * @param outcome     which way the step went ({@code true}, {@code approved} ...), null for single-exit steps
     * @param waitingUntil set while the step waits (an approval): the deadline
     */
    record StepView(String key, String type, String status, String outcome, int attempt, int maxAttempts,
                    int timeoutSeconds, String startedAt, String finishedAt, String nextAttemptAt, String waitingUntil,
                    ErrorInfo error, Map<String, Object> output) {
    }

    record EventView(long id, String type, String stepKey, String message, String occurredAt) {
    }

    record RunDetail(long id, FlowRef flow, String status, boolean cancelRequested, String trigger,
                     String triggeredBy, UserRef runAs, Long scheduleId, String createdAt, String startedAt,
                     String finishedAt, ErrorInfo error, List<StepView> steps, List<FileView> files,
                     List<EventView> events, Object triggerPayload, GraphView definition) {
    }

    /** The flow as this run executes it (a run keeps its own copy; the flow may have changed since). */
    record GraphView(List<FlowStep> steps, List<FlowEdge> edges, Map<String, FlowDefinition.Position> layout) {
    }

    static RunSummary summary(FlowRun run) {
        return new RunSummary(run.getId(), new FlowRef(run.getFlowId(), run.getFlowName()), value(run.getStatus()),
                run.isCancelRequested(), value(run.getTriggerType()), run.getTriggeredBy(),
                new UserRef(run.getRunAs()), run.getScheduleId(), time(run.getCreatedAt()), time(run.getStartedAt()),
                time(run.getFinishedAt()), ErrorInfo.of(run.getErrorCode(), run.getErrorMessage()));
    }

    static RunDetail detail(FlowRun run, List<StepRun> steps, List<RunEvent> events, List<RunArtifact> artifacts,
                            java.time.Instant now) {
        List<StepView> stepViews = new ArrayList<>();
        Map<Long, String> stepKeys = new java.util.HashMap<>();
        for (StepRun step : steps) {
            stepKeys.put(step.getId(), step.getStepKey());
            Map<String, Object> output = output(step.getOutputJson());
            stepViews.add(new StepView(step.getStepKey(), step.getStepType(), value(step.getStatus()),
                    step.getOutcome(), step.getAttempt(), step.getMaxAttempts(), step.getTimeoutSeconds(),
                    time(step.getStartedAt()), time(step.getFinishedAt()),
                    step.getStatus() == StepStatus.READY ? time(step.getAvailableAt()) : null,
                    step.getStatus() == StepStatus.WAITING ? time(step.getAvailableAt()) : null,
                    ErrorInfo.of(step.getErrorCode(), step.getErrorMessage()), output));
        }
        List<FileView> files = artifacts.stream().map(file -> {
            boolean available = file.isAvailable(now);
            return new FileView(file.getId(), stepKeys.get(file.getStepRunId()), file.getFileName(),
                    file.getSizeBytes(), time(file.getExpiresAt()), !available,
                    available ? "/api/flow-files/" + file.getId() + "/download" : null);
        }).toList();
        return new RunDetail(run.getId(), new FlowRef(run.getFlowId(), run.getFlowName()), value(run.getStatus()),
                run.isCancelRequested(), value(run.getTriggerType()), run.getTriggeredBy(),
                new UserRef(run.getRunAs()), run.getScheduleId(), time(run.getCreatedAt()), time(run.getStartedAt()),
                time(run.getFinishedAt()), ErrorInfo.of(run.getErrorCode(), run.getErrorMessage()), stepViews, files,
                events.stream().map(event -> new EventView(event.getId(), event.getType(), event.getStepKey(),
                        event.getMessage(), time(event.getOccurredAt()))).toList(),
                run.getTriggerPayloadJson() == null ? null : JSON.readValue(run.getTriggerPayloadJson(), Object.class),
                graph(run.definition()));
    }

    private static GraphView graph(FlowDefinition definition) {
        return new GraphView(definition.steps(), definition.edges(), definition.layout());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> output(String json) {
        return json == null ? null : JSON.readValue(json, Map.class);
    }

}
