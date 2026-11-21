package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.Kind;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Event triggers of flows: webhooks (an HTTP call signed with the trigger's secret) and Kafka topics. Visible and
 * changeable by whoever may see the flow. The run acts as the flow owner, like scheduled runs.
 */
@Slf4j
@Service
public class TriggerService {

    /** A trigger as entered by the user. {@code filter}: optional JSON Logic over {@code {trigger}}. */
    public record TriggerInput(FlowTrigger.Type type, boolean enabled, String topic, Object filter) {
    }

    /** What happened to an incoming event. */
    public record Delivery(String status, Long runId) {
        static Delivery started(long runId) {
            return new Delivery("started", runId);
        }
    }

    private static final int MAX_TRIGGERS_PER_FLOW = 10;
    private static final Duration INBOX_RETENTION = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FlowTriggerRepository triggers;
    private final TriggerEventRepository events;
    private final FlowService flows;
    private final RunStore store;
    private final FlowEngine engine;
    private final FlowProperties properties;
    private final Clock clock;

    TriggerService(FlowTriggerRepository triggers, TriggerEventRepository events, FlowService flows, RunStore store,
                   FlowEngine engine, FlowProperties properties, Clock clock) {
        this.triggers = triggers;
        this.events = events;
        this.flows = flows;
        this.store = store;
        this.engine = engine;
        this.properties = properties;
        this.clock = clock;
    }

    // ---- managing -------------------------------------------------------------------------------------

    public List<FlowTrigger> list(long flowId, Actor viewer) {
        flows.get(flowId, viewer);
        return triggers.findByFlowIdOrderById(flowId);
    }

    public FlowTrigger get(long id, Actor viewer) {
        FlowTrigger trigger = triggers.findById(id).orElseThrow(() -> notFound(id));
        try {
            flows.get(trigger.getFlowId(), viewer);
        } catch (FlowException e) {
            throw notFound(id);
        }
        return trigger;
    }

    @Transactional
    public FlowTrigger create(long flowId, TriggerInput input, Actor viewer) {
        flows.get(flowId, viewer);
        if (triggers.countByFlowId(flowId) >= MAX_TRIGGERS_PER_FLOW) {
            throw new FlowException(Kind.CONFLICT, "TRIGGER_LIMIT_REACHED",
                    "A flow may have at most " + MAX_TRIGGERS_PER_FLOW + " triggers.");
        }
        String filterJson = check(input);
        Instant now = clock.instant();
        FlowTrigger trigger = new FlowTrigger(flowId, input.type(), viewer.username(), now);
        trigger.define(input.enabled(), input.topic() == null ? null : input.topic().strip(), filterJson, now);
        if (input.type() == FlowTrigger.Type.WEBHOOK) {
            trigger.issueWebhookCredentials(randomToken(), randomToken());
        }
        return triggers.saveAndFlush(trigger);
    }

    @Transactional
    public FlowTrigger update(long id, long expectedVersion, TriggerInput input, Actor viewer) {
        FlowTrigger trigger = get(id, viewer);
        if (trigger.getVersion() != expectedVersion) {
            throw versionMismatch();
        }
        if (input.type() != trigger.getType()) {
            throw FlowException.invalid("type", "TRIGGER_TYPE_FIXED", "The type of a trigger cannot change.");
        }
        String filterJson = check(input);
        trigger.define(input.enabled(), input.topic() == null ? null : input.topic().strip(), filterJson,
                clock.instant());
        try {
            return triggers.saveAndFlush(trigger);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw versionMismatch();
        }
    }

    /** A new webhook secret; the old one stops working at once. */
    @Transactional
    public FlowTrigger rotateSecret(long id, Actor viewer) {
        FlowTrigger trigger = get(id, viewer);
        if (trigger.getType() != FlowTrigger.Type.WEBHOOK) {
            throw new FlowException(Kind.CONFLICT, "NOT_A_WEBHOOK", "Only webhook triggers have a secret.");
        }
        trigger.rotateSecret(randomToken(), clock.instant());
        return triggers.saveAndFlush(trigger);
    }

    @Transactional
    public void delete(long id, Actor viewer) {
        triggers.delete(get(id, viewer));
    }

    private String check(TriggerInput input) {
        List<FieldProblem> problems = new ArrayList<>();
        if (input.type() == FlowTrigger.Type.KAFKA) {
            if (!properties.kafka().enabled()) {
                problems.add(new FieldProblem("type", "KAFKA_DISABLED", "Kafka is not enabled (flows.kafka.enabled)."));
            } else if (input.topic() == null || input.topic().isBlank()) {
                problems.add(new FieldProblem("topic", "REQUIRED", "A Kafka trigger needs a topic."));
            } else if (!input.topic().strip().matches("[a-zA-Z0-9._-]{1,249}")) {
                problems.add(new FieldProblem("topic", "INVALID_FORMAT", "Not a valid topic name."));
            } else if (!properties.kafka().allows(input.topic().strip())) {
                problems.add(new FieldProblem("topic", "TOPIC_NOT_ALLOWED", "Listening on " + input.topic()
                        + " is not allowed (flows.kafka.allowed-topics)."));
            }
        }
        if (input.filter() != null) {
            try {
                JsonLogic.check(input.filter());
            } catch (JsonLogic.InvalidRuleException e) {
                problems.add(new FieldProblem("filter", "INVALID_EXPRESSION", e.getMessage()));
            }
        }
        if (!problems.isEmpty()) {
            throw FlowException.invalid(problems);
        }
        return input.filter() == null ? null : FlowDefinition.toJson(input.filter());
    }

    // ---- receiving ------------------------------------------------------------------------------------

    /**
     * An incoming webhook. The body must be signed: {@code X-Signature: sha256=<hex HMAC-SHA256 of the raw body with
     * the trigger's secret>}. {@code X-Event-Id} (optional) makes redeliveries of the same event start no second run.
     */
    public Delivery receiveWebhook(String token, byte[] body, String signature, String eventId) {
        FlowTrigger trigger = triggers.findByWebhookToken(token).filter(FlowTrigger::isEnabled)
                .orElseThrow(() -> FlowException.notFound("TRIGGER_NOT_FOUND", "No active webhook at this address."));
        if (body.length > properties.webhook().maxPayloadSize().toBytes()) {
            throw new FlowException(Kind.BAD_REQUEST, "PAYLOAD_TOO_LARGE", "The body is larger than "
                    + properties.webhook().maxPayloadSize() + ".");
        }
        if (!validSignature(trigger.getWebhookSecret(), body, signature)) {
            throw new FlowException(Kind.UNAUTHORIZED, "INVALID_SIGNATURE",
                    "X-Signature must be sha256=<HMAC-SHA256 of the body with the trigger secret>.");
        }
        Object payload;
        try {
            payload = body.length == 0 ? Map.of() : FlowDefinition.valueFromJson(new String(body, UTF_8));
        } catch (JacksonException e) {
            throw new FlowException(Kind.BAD_REQUEST, "MALFORMED_REQUEST", "The body must be JSON.");
        }
        if (eventId != null && (eventId.isBlank() || eventId.length() > 200)) {
            throw new FlowException(Kind.BAD_REQUEST, "INVALID_PARAMETER", "X-Event-Id must be 1-200 characters.");
        }
        return deliver(trigger, eventId, payload);
    }

    /** A Kafka message on a topic some triggers listen on; each matching trigger starts a run. */
    void receiveKafka(String topic, int partition, long offset, String key, String value, Map<String, String> headers) {
        Object parsedValue;
        try {
            parsedValue = value == null ? null : FlowDefinition.valueFromJson(value);
        } catch (JacksonException e) {
            parsedValue = value;   // not JSON: the text itself
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("topic", topic);
        payload.put("partition", partition);
        payload.put("offset", offset);
        payload.put("key", key);
        payload.put("value", parsedValue);
        payload.put("headers", headers);
        for (FlowTrigger trigger : triggers.findByTypeAndEnabledTrueAndTopic(FlowTrigger.Type.KAFKA, topic)) {
            Delivery delivery = deliver(trigger, topic + ":" + partition + ":" + offset, payload);
            log.debug("Kafka {}:{}:{} for trigger {}: {}", topic, partition, offset, trigger.getId(), delivery.status());
        }
    }

    private Delivery deliver(FlowTrigger trigger, String eventKey, Object payload) {
        Object filter = trigger.filter();
        if (filter != null) {
            boolean accepted;
            try {
                accepted = JsonLogic.test(filter, Map.of("trigger", payload == null ? Map.of() : payload));
            } catch (RuntimeException e) {
                log.info("Filter of trigger {} failed on an event: {}", trigger.getId(), e.getMessage());
                accepted = false;
            }
            if (!accepted) {
                return new Delivery("filtered", null);
            }
        }
        Optional<FlowRun> run;
        try {
            run = store.fireTrigger(trigger, eventKey, payload);
        } catch (DataIntegrityViolationException e) {
            return new Delivery("duplicate", null);   // the same event arrived twice at the same moment
        }
        if (run.isEmpty()) {
            return new Delivery("duplicate", null);
        }
        engine.wakeUp();
        return Delivery.started(run.get().getId());
    }

    static boolean validSignature(String secret, byte[] body, String header) {
        if (secret == null || header == null || !header.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(body);
            byte[] given = HexFormat.of().parseHex(header.substring("sha256=".length()).strip());
            return MessageDigest.isEqual(expected, given);   // constant time
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            return false;
        }
    }

    /** Every instance runs this; deleting old inbox rows is idempotent. */
    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT1H")
    void cleanUpInbox() {
        events.deleteOlderThan(clock.instant().minus(INBOX_RETENTION));
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static FlowException notFound(long id) {
        return FlowException.notFound("TRIGGER_NOT_FOUND", "Trigger " + id + " does not exist.");
    }

    private static FlowException versionMismatch() {
        return new FlowException(Kind.PRECONDITION_FAILED, "VERSION_MISMATCH",
                "The trigger was changed by someone else; reload it and try again.");
    }
}
