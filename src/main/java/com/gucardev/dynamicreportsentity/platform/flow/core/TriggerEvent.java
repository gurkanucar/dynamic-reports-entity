package com.gucardev.dynamicreportsentity.platform.flow.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Inbox of events that started runs. Senders deliver at least once (webhook retries, Kafka redelivery after a
 * rebalance); the unique {@code (trigger, eventKey)} makes the second delivery a no-op. Written in the transaction
 * that creates the run.
 */
@Entity
@Table(name = "flow_trigger_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_flow_trigger_event", columnNames = {"trigger_id", "event_key"}),
        indexes = @Index(name = "ix_flow_trigger_event_received", columnList = "received_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class TriggerEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trigger_id", nullable = false)
    private Long triggerId;

    /** {@code X-Event-Id} of a webhook, {@code topic:partition:offset} of a Kafka message. */
    @Column(name = "event_key", nullable = false, length = 200)
    private String eventKey;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    private Long runId;

    TriggerEvent(Long triggerId, String eventKey, Instant receivedAt, Long runId) {
        this.triggerId = triggerId;
        this.eventKey = eventKey;
        this.receivedAt = receivedAt;
        this.runId = runId;
    }
}
