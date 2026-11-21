package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Starts a flow when an event arrives: an HTTP call to the trigger's webhook URL, or a message on a Kafka topic. The
 * event becomes {@code trigger} for the run's steps; an optional JSON Logic {@code filter} decides which events start
 * a run at all. (Cron schedules are {@link FlowSchedule}s.)
 */
@Entity
@Table(name = "flow_trigger",
        uniqueConstraints = @UniqueConstraint(name = "uk_flow_trigger_token", columnNames = "webhook_token"),
        indexes = {
                @Index(name = "ix_flow_trigger_flow", columnList = "flow_id"),
                @Index(name = "ix_flow_trigger_topic", columnList = "trigger_type, enabled, topic")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlowTrigger {

    /** In JSON as {@code webhook} / {@code kafka} (snake_case enums, API guideline). */
    public enum Type {
        @JsonProperty("webhook") WEBHOOK,
        @JsonProperty("kafka") KAFKA,
        /** An event of another module ({@code topic} is its source, e.g. {@code survey:42}). */
        @JsonProperty("event") EVENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Column(name = "flow_id", nullable = false)
    private Long flowId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private Type type;

    private boolean enabled;

    /** KAFKA: the topic listened on; EVENT: the source ({@code survey:42}). */
    @Column(length = 249)
    private String topic;

    /** Optional JSON Logic over {@code {trigger}}; events it rejects start no run. */
    @Column(name = "filter_json", length = 20_000)
    private String filterJson;

    /** WEBHOOK: the secret part of the URL ({@code /api/flow-hooks/{token}}). */
    @Column(name = "webhook_token", length = 64)
    private String webhookToken;

    /** WEBHOOK: key of the HMAC-SHA256 signature callers send in {@code X-Signature}. */
    @Column(name = "webhook_secret", length = 64)
    private String webhookSecret;

    @Column(nullable = false, length = 100)
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant lastFiredAt;

    private Long lastRunId;

    FlowTrigger(Long flowId, Type type, String createdBy, Instant now) {
        this.flowId = flowId;
        this.type = type;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    void define(boolean enabled, String topic, String filterJson, Instant now) {
        this.enabled = enabled;
        this.topic = type == Type.KAFKA || type == Type.EVENT ? topic : null;
        this.filterJson = filterJson;
        this.updatedAt = now;
    }

    void issueWebhookCredentials(String token, String secret) {
        this.webhookToken = token;
        this.webhookSecret = secret;
    }

    void rotateSecret(String secret, Instant now) {
        this.webhookSecret = secret;
        this.updatedAt = now;
    }

    Object filter() {
        return FlowDefinition.valueFromJson(filterJson);
    }
}
