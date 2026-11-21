package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowStep;
import com.gucardev.dynamicreportsentity.platform.flow.spi.Lane;
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

import java.time.Duration;
import java.time.Instant;

/**
 * One step of a run: the work queue of the engine. Instances pick READY rows of their lanes (highest
 * {@link #priority} first, then oldest {@link #availableAt}), claim them under a lease ({@link #owner} is a
 * token unique per claim) and renew the lease while they work. A step whose lease runs out is retried.
 */
@Entity
@Table(name = "flow_step_run", indexes = {
        @Index(name = "ix_flow_step_run_ready", columnList = "lane, status, available_at"),
        @Index(name = "ix_flow_step_run_lease", columnList = "status, lease_until"),
        @Index(name = "ix_flow_step_run_run", columnList = "run_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StepRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    private int stepIndex;

    @Column(nullable = false, length = 40)
    private String stepKey;

    @Column(nullable = false, length = 30)
    private String stepType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Lane lane;

    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StepStatus status;

    /** Attempts started so far (a claim starts one). */
    private int attempt;

    private int maxAttempts;

    private int timeoutSeconds;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    /** Claim token of the instance working on it. */
    @Column(length = 120)
    private String owner;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    private Instant startedAt;
    private Instant finishedAt;

    @Column(length = 60)
    private String errorCode;

    @Column(length = 1000)
    private String errorMessage;

    /** Which way the step went ({@code true}, {@code approved} ...), null for steps with a single way out. */
    @Column(length = 20)
    private String outcome;

    /** Output for later steps (JSON object). */
    @Column(name = "output_json", length = 1_000_000)
    private String outputJson;

    StepRun(FlowRun run, int stepIndex, FlowStep step, int maxAttempts, Duration timeout, Instant now) {
        this.runId = run.getId();
        this.stepIndex = stepIndex;
        this.stepKey = step.key();
        this.stepType = step.typeName();
        this.lane = step.lane();
        this.priority = run.getPriority();
        this.status = StepStatus.READY;
        this.maxAttempts = maxAttempts;
        this.timeoutSeconds = Math.toIntExact(timeout.toSeconds());
        this.availableAt = now;
    }

    boolean isClaimedBy(String token) {
        return status == StepStatus.RUNNING && token.equals(owner);
    }

    void claim(String token, Instant now, Instant leaseUntil) {
        this.status = StepStatus.RUNNING;
        this.owner = token;
        this.leaseUntil = leaseUntil;
        this.attempt++;
        this.startedAt = now;
        this.finishedAt = null;
    }

    void completed(Instant now, String outputJson, String outcome) {
        finish(StepStatus.COMPLETED, now, null, null);
        this.outputJson = outputJson;
        this.outcome = outcome;
    }

    /** The attempt is done but the step waits (an approval) until {@code until} at most; it holds no lease. */
    void waiting(String outputJson, Instant until) {
        this.status = StepStatus.WAITING;
        this.outputJson = outputJson;
        this.availableAt = until;
        this.owner = null;
        this.leaseUntil = null;
    }

    void failed(Instant now, String code, String message) {
        finish(StepStatus.FAILED, now, code, message);
    }

    void cancelled(Instant now) {
        finish(StepStatus.CANCELLED, now, errorCode, errorMessage);
    }

    /** Failed attempt that will be tried again at {@code at}. */
    void retryAt(Instant at, String code, String message) {
        this.status = StepStatus.READY;
        this.availableAt = at;
        this.owner = null;
        this.leaseUntil = null;
        this.errorCode = code;
        this.errorMessage = Texts.truncate(message, 1000);
    }

    /** Handed back without counting as an attempt (the instance shut down). */
    void release(Instant now) {
        this.status = StepStatus.READY;
        this.availableAt = now;
        this.owner = null;
        this.leaseUntil = null;
        this.attempt = Math.max(0, attempt - 1);
    }

    private void finish(StepStatus finalStatus, Instant now, String code, String message) {
        this.status = finalStatus;
        this.finishedAt = now;
        this.owner = null;
        this.leaseUntil = null;
        this.errorCode = code;
        this.errorMessage = Texts.truncate(message, 1000);
    }
}
