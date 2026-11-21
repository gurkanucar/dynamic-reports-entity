package com.gucardev.dynamicreportsentity.platform.flow.core;

import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Starts a flow on a cron expression, in a time zone. Every instance looks for due schedules; firing locks the
 * schedule row and moves {@link #nextFireAt} forward in the same transaction that creates the run, so each
 * fire time starts exactly one run. Fire times missed while no instance was up are fired once, not replayed.
 */
@Entity
@Table(name = "flow_schedule", indexes = {
        @Index(name = "ix_flow_schedule_due", columnList = "enabled, next_fire_at"),
        @Index(name = "ix_flow_schedule_flow", columnList = "flow_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlowSchedule {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Version
    private long version;

    @Column(name = "flow_id", nullable = false)
    private UUID flowId;

    /** As entered: 5 fields (minute precision) or 6 (with seconds), or a macro like {@code @daily}. */
    @Column(nullable = false, length = 100)
    private String cron;

    @Column(nullable = false, length = 60)
    private String timeZone;

    private boolean enabled;

    /** Null while disabled. */
    @Column(name = "next_fire_at")
    private Instant nextFireAt;

    private Instant lastFiredAt;

    private UUID lastRunId;

    /** Why the last fire time did not start a run (e.g. the previous run was still active). */
    @Column(length = 300)
    private String lastSkipReason;

    @Column(nullable = false, length = 100)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    FlowSchedule(UUID flowId, String createdBy, Instant now) {
        this.flowId = flowId;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    void define(String cron, String timeZone, boolean enabled, Instant nextFireAt, Instant now) {
        this.cron = cron;
        this.timeZone = timeZone;
        this.enabled = enabled;
        this.nextFireAt = enabled ? nextFireAt : null;
        this.updatedAt = now;
    }

    void fired(Instant fireTime, Instant next, UUID runId, String skipReason) {
        this.lastFiredAt = fireTime;
        this.nextFireAt = next;
        this.lastRunId = runId == null ? lastRunId : runId;
        this.lastSkipReason = skipReason;
    }
}
