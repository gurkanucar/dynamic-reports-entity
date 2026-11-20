package com.gucardev.dynamicreportsentity.platform.flow.web;

import com.gucardev.dynamicreportsentity.platform.flow.core.Flow;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowRun;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowSchedule;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowStep;
import com.gucardev.dynamicreportsentity.platform.flow.core.RunArtifact;
import com.gucardev.dynamicreportsentity.platform.flow.core.RunEvent;
import com.gucardev.dynamicreportsentity.platform.flow.core.StepRun;
import com.gucardev.dynamicreportsentity.platform.flow.mail.MailTemplate;
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

    record FlowDetail(long id, String name, String description, UserRef owner, List<FlowStep> steps, long version,
                      String createdAt, String updatedAt, UserRef updatedBy) {
    }

    static FlowSummary summary(Flow flow) {
        return new FlowSummary(flow.getId(), flow.getName(), flow.getDescription(), new UserRef(flow.getOwner()),
                flow.definition().steps().size(), flow.getVersion(), time(flow.getCreatedAt()),
                time(flow.getUpdatedAt()));
    }

    static FlowDetail detail(Flow flow) {
        return new FlowDetail(flow.getId(), flow.getName(), flow.getDescription(), new UserRef(flow.getOwner()),
                flow.definition().steps(), flow.getVersion(), time(flow.getCreatedAt()), time(flow.getUpdatedAt()),
                new UserRef(flow.getUpdatedBy()));
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

    record StepView(String key, String type, String status, int attempt, int maxAttempts, int timeoutSeconds,
                    String startedAt, String finishedAt, String nextAttemptAt, ErrorInfo error,
                    Map<String, Object> output) {
    }

    record EventView(long id, String type, String stepKey, String message, String occurredAt) {
    }

    record RunDetail(long id, FlowRef flow, String status, boolean cancelRequested, String trigger,
                     String triggeredBy, UserRef runAs, Long scheduleId, String createdAt, String startedAt,
                     String finishedAt, ErrorInfo error, List<StepView> steps, List<FileView> files,
                     List<EventView> events) {
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
                    step.getAttempt(), step.getMaxAttempts(), step.getTimeoutSeconds(), time(step.getStartedAt()),
                    time(step.getFinishedAt()),
                    step.getStatus().name().equals("READY") ? time(step.getAvailableAt()) : null,
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
                        event.getMessage(), time(event.getOccurredAt()))).toList());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> output(String json) {
        return json == null ? null : JSON.readValue(json, Map.class);
    }

    // ---- mail templates -------------------------------------------------------------------------------

    record TemplateSummary(long id, String name, String description, UserRef owner, long version, String updatedAt) {
    }

    record TemplateDetail(long id, String name, String description, String subject, String bodyHtml, UserRef owner,
                          long version, String createdAt, String updatedAt, UserRef updatedBy) {
    }

    static TemplateSummary summary(MailTemplate template) {
        return new TemplateSummary(template.getId(), template.getName(), template.getDescription(),
                new UserRef(template.getOwner()), template.getVersion(), time(template.getUpdatedAt()));
    }

    static TemplateDetail detail(MailTemplate template) {
        return new TemplateDetail(template.getId(), template.getName(), template.getDescription(),
                template.getSubject(), template.getBodyHtml(), new UserRef(template.getOwner()),
                template.getVersion(), time(template.getCreatedAt()), time(template.getUpdatedAt()),
                new UserRef(template.getUpdatedBy()));
    }
}
