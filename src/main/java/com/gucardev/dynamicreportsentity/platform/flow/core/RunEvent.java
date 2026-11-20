package com.gucardev.dynamicreportsentity.platform.flow.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Append-only history of a run (audit trail and the timeline on screen). Never updated. */
@Entity
@Table(name = "flow_run_event", indexes = @Index(name = "ix_flow_run_event_run", columnList = "run_id, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RunEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(nullable = false)
    private Instant occurredAt;

    /** snake_case, e.g. {@code step_retry_scheduled}. */
    @Column(nullable = false, length = 40)
    private String type;

    @Column(length = 40)
    private String stepKey;

    @Column(length = 1000)
    private String message;

    RunEvent(Long runId, Instant occurredAt, String type, String stepKey, String message) {
        this.runId = runId;
        this.occurredAt = occurredAt;
        this.type = type;
        this.stepKey = stepKey;
        this.message = Texts.truncate(message, 1000);
    }
}
