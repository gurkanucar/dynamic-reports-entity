package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.spi.TriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One execution of a flow, with its own copy of the definition. State changes lock this row
 * ({@link FlowRunRepository#lock}), so a finishing step, a cancel and a lease takeover never interleave.
 */
@Entity
@Table(name = "flow_run", indexes = {
        @Index(name = "ix_flow_run_flow", columnList = "flow_id, status"),
        @Index(name = "ix_flow_run_run_as", columnList = "run_as, status")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlowRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flow_id", nullable = false)
    private Long flowId;

    @Column(nullable = false, length = 100)
    private String flowName;

    @Column(name = "definition_json", nullable = false, length = 200_000)
    private String definitionJson;

    /** The flow owner: the datasets run with their permissions. */
    @Column(name = "run_as", nullable = false, length = 100)
    private String runAs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TriggerType triggerType;

    /** User who started it, or {@code schedule:<id>}. */
    @Column(name = "triggered_by", nullable = false, length = 100)
    private String triggeredBy;

    private Long scheduleId;

    /** Webhook body or Kafka message that started the run (JSON); null for manual and scheduled runs. */
    @Column(name = "trigger_payload_json", length = 300_000)
    private String triggerPayloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunStatus status;

    private boolean cancelRequested;

    /** Higher first; manual runs before scheduled ones. */
    private int priority;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private Instant startedAt;
    private Instant finishedAt;

    @Column(length = 60)
    private String errorCode;

    @Column(length = 1000)
    private String errorMessage;

    FlowRun(Flow flow, TriggerType triggerType, String triggeredBy, Long scheduleId, int priority, Instant now,
            String triggerPayloadJson) {
        this.triggerPayloadJson = triggerPayloadJson;
        this.flowId = flow.getId();
        this.flowName = flow.getName();
        this.definitionJson = flow.getDefinitionJson();
        this.runAs = flow.getOwner();
        this.triggerType = triggerType;
        this.triggeredBy = triggeredBy;
        this.scheduleId = scheduleId;
        this.priority = priority;
        this.status = RunStatus.QUEUED;
        this.createdAt = now;
    }

    public FlowDefinition definition() {
        return FlowDefinition.fromJson(definitionJson);
    }

    void started(Instant now) {
        if (status == RunStatus.QUEUED) {
            status = RunStatus.RUNNING;
            startedAt = now;
        }
    }

    void requestCancel() {
        cancelRequested = true;
    }

    void completed(Instant now) {
        finish(RunStatus.COMPLETED, now, null, null);
    }

    void failed(Instant now, String code, String message) {
        finish(RunStatus.FAILED, now, code, message);
    }

    void cancelled(Instant now) {
        finish(RunStatus.CANCELLED, now, null, null);
    }

    private void finish(RunStatus finalStatus, Instant now, String code, String message) {
        status = finalStatus;
        finishedAt = now;
        errorCode = code;
        errorMessage = Texts.truncate(message, 1000);
    }
}
