package com.gucardev.dynamicreportsentity.platform.flow;

import com.sun.net.httpserver.HttpServer;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Phase 3 end to end: graphs with conditions, approvals by groups, webhook triggers, filter and webhook steps.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FlowGraphIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final RequestPostProcessor ADMIN = as("admin", "PLATFORM_USER", "PLATFORM_ADMIN", "SALARY_VIEW",
            "ALL_DEPARTMENTS");
    private static final RequestPostProcessor BOB = as("bob", "PLATFORM_USER", "SALARY_VIEW");
    private static final RequestPostProcessor CAROL = as("carol", "PLATFORM_USER");
    private static final AtomicInteger NAMES = new AtomicInteger();

    /** A small HTTP server playing the system a webhook step calls; answers with the queued status codes. */
    private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();
    private static final List<Integer> ANSWERS = new CopyOnWriteArrayList<>();
    private static final HttpServer TARGET = start();

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/hook", exchange -> {
                RECEIVED.add(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
                int status = ANSWERS.isEmpty() ? 200 : ANSWERS.removeFirst();
                byte[] body = "{\"ticket\": \"T-1\"}".getBytes(UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stopTarget() {
        TARGET.stop(0);
    }

    @Autowired
    MockMvcTester mvc;
    @Autowired
    JdbcTemplate jdbc;

    private static RequestPostProcessor as(String name, String... authorities) {
        return user(name).authorities(Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    @BeforeEach
    void reset() {
        RecordingMailSender.reset();
        RECEIVED.clear();
        ANSWERS.clear();
    }

    // ---- helpers --------------------------------------------------------------------------------------

    private static JsonNode json(MvcTestResult result) {
        return JSON.readTree(result.getResponse().getContentAsByteArray());
    }

    private MvcTestResult post(String uri, RequestPostProcessor user, Object body) {
        return mvc.post().uri(uri).with(user).contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String text ? text : JSON.writeValueAsString(body)).exchange();
    }

    private long template() {
        MvcTestResult created = post("/api/mail-templates", ADMIN, Map.of("name", "Graph " + NAMES.incrementAndGet(),
                "subject", "{{flow.name}}", "bodyHtml", "<p>{{run.id}}</p>"));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return json(created).get("data").get("id").asLong();
    }

    private long group(String... members) {
        List<Map<String, String>> list = new ArrayList<>();
        for (String member : members) {
            list.add(Map.of("username", member, "email", member + "@example.com"));
        }
        MvcTestResult created = post("/api/approval-groups", ADMIN,
                Map.of("name", "Approvers " + NAMES.incrementAndGet(), "members", list));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return json(created).get("data").get("id").asLong();
    }

    private Map<String, Object> mail(String key, long template, String to) {
        return Map.of("type", "mail", "key", key, "templateId", template, "to", List.of(to));
    }

    /** Without notifications: they are sent after the commit and could land in the next test's mailbox. */
    private Map<String, Object> approval(String key, long group) {
        return approval(key, group, false);
    }

    private Map<String, Object> approval(String key, long group, boolean notify) {
        return Map.of("type", "approval", "key", key, "groupId", group, "notifyMembers", notify,
                "title", "Approve {{flow.name}}", "description", "Run {{run.id}} needs a decision");
    }

    private long flow(List<?> steps, List<?> edges) {
        MvcTestResult created = post("/api/flows", ADMIN, Map.of("name", "Graph flow " + NAMES.incrementAndGet(),
                "steps", steps, "edges", edges));
        assertThat(created).as(new String(created.getResponse().getContentAsByteArray(), UTF_8))
                .hasStatus(HttpStatus.CREATED);
        return json(created).get("data").get("id").asLong();
    }

    private long start(long flowId) {
        MvcTestResult result = mvc.post().uri("/api/flows/" + flowId + "/runs").with(ADMIN).exchange();
        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        return json(result).get("data").get("id").asLong();
    }

    private JsonNode run(long runId) {
        return json(mvc.get().uri("/api/flow-runs/" + runId).with(ADMIN).exchange()).get("data");
    }

    private JsonNode await(long runId, String... statuses) {
        Set<String> wanted = Set.of(statuses);
        JsonNode[] last = new JsonNode[1];
        await(() -> wanted.contains((last[0] = run(runId)).get("status").asString()), () -> "run " + runId
                + " " + wanted + ", last " + last[0]);
        return last[0];
    }

    private JsonNode awaitWaiting(long runId) {
        JsonNode[] last = new JsonNode[1];
        await(() -> {
            last[0] = run(runId);
            for (JsonNode step : last[0].get("steps")) {
                if (step.get("status").asString().equals("waiting")) {
                    return true;
                }
            }
            return false;
        }, () -> "a waiting step in run " + runId + ", last " + last[0]);
        return last[0];
    }

    private static void await(BooleanSupplier condition, java.util.function.Supplier<String> what) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Timed out waiting for " + what.get());
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static List<String> keys(JsonNode run) {
        List<String> keys = new ArrayList<>();
        run.get("steps").forEach(step -> keys.add(step.get("key").asString() + ":" + step.get("status").asString()
                + (step.get("outcome").isNull() ? "" : ":" + step.get("outcome").asString())));
        return keys;
    }

    private static List<String> recipients() {
        return RecordingMailSender.SENT.stream().flatMap(message -> {
            try {
                return Arrays.stream(message.getAllRecipients() == null ? new jakarta.mail.Address[0]
                        : message.getAllRecipients()).map(Object::toString);
            } catch (jakarta.mail.MessagingException e) {
                throw new IllegalStateException(e);
            }
        }).toList();
    }

    private long pendingRequest(RequestPostProcessor user) {
        JsonNode list = json(mvc.get().uri("/api/approval-requests?status=pending&size=100").with(user).exchange());
        assertThat(list.get("data").size()).isGreaterThan(0);
        return list.get("data").get(0).get("id").asLong();
    }

    /** Status of the approval request the run's step {@code ask} opened (the approval module knows it by step run). */
    private String requestStatus(long runId) {
        return jdbc.queryForObject("SELECT r.status FROM approval_request r WHERE r.source_type = 'flow-step'"
                + " AND r.source_id = (SELECT CAST(s.id AS VARCHAR(100)) FROM flow_step_run s"
                + " WHERE s.run_id = ? AND s.step_key = 'ask')", String.class, runId);
    }

    private static String sign(String secret, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    // ---- saved datasets ---------------------------------------------------------------------------------

    @Test
    void aStepUsesASavedDatasetByNameWhichThenCannotBeDeleted() {
        assertThat(post("/api/saved-datasets", ADMIN, Map.of("name", "g_headcount", "definition", Map.of(
                "root", "Personnel", "columns", List.of(Map.of("field", "department.name"),
                        Map.of("aggregate", "COUNT", "label", "Headcount"))))))
                .hasStatus(HttpStatus.CREATED);
        // exactly one source: a saved dataset or an own definition
        assertThat(post("/api/flows", ADMIN, Map.of("name", "Both", "steps", List.of(Map.of("type", "dataset",
                "key", "data", "datasetName", "g_headcount", "definition", Map.of("root", "Personnel",
                        "columns", List.of(Map.of("field", "fullName"))))))))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.errors[0].code")
                .isEqualTo("DATASET_SOURCE_REQUIRED");
        assertThat(post("/api/flows", ADMIN, Map.of("name", "Missing", "steps", List.of(Map.of("type", "dataset",
                "key", "data", "datasetName", "g_nope")))))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.errors[0].code")
                .isEqualTo("SAVED_DATASET_NOT_FOUND");

        long flow = flow(List.of(Map.of("type", "dataset", "key", "data", "datasetName", "g_headcount")), List.of());
        JsonNode run = await(start(flow), "completed");
        assertThat(run.get("steps").get(0).get("output").get("rowCount").asInt()).isPositive();
        assertThat(mvc.delete().uri("/api/saved-datasets/g_headcount").with(ADMIN).exchange()).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("SAVED_DATASET_IN_USE");
    }

    // ---- conditions -----------------------------------------------------------------------------------

    @Test
    void aConditionPicksTheBranch() {
        long template = template();
        Map<String, Object> dataset = Map.of("type", "dataset", "key", "dataset", "definition", Map.of(
                "root", "Personnel", "columns", List.of(Map.of("field", "fullName")),
                "where", Map.of("field", "fullName", "operator", "EQ", "value", "Nobody Here")));
        long flow = flow(List.of(dataset,
                        Map.of("type", "condition", "key", "check",
                                "expression", Map.of(">", List.of(Map.of("var", "steps.dataset.rowCount"), 0))),
                        mail("has_rows", template, "rows@example.com"),
                        mail("empty", template, "empty@example.com")),
                List.of(Map.of("from", "dataset", "to", "check"),
                        Map.of("from", "check", "to", "has_rows", "when", "true"),
                        Map.of("from", "check", "to", "empty", "when", "false")));

        JsonNode run = await(start(flow), "completed");

        assertThat(keys(run)).containsExactly("dataset:completed", "check:completed:false", "empty:completed");
        assertThat(recipients()).containsExactly("empty@example.com");
    }

    // ---- approvals ------------------------------------------------------------------------------------

    @Test
    void aGroupMemberApprovesAndTheRunGoesOn() throws Exception {
        long template = template();
        long group = group("bob", "admin");
        long flow = flow(List.of(approval("ask", group, true), mail("ok", template, "ok@example.com"),
                        mail("no", template, "no@example.com")),
                List.of(Map.of("from", "ask", "to", "ok", "when", "approved"),
                        Map.of("from", "ask", "to", "no", "when", "rejected")));
        long runId = start(flow);
        JsonNode waiting = awaitWaiting(runId);
        assertThat(waiting.get("status").asString()).isEqualTo("running");
        assertThat(waiting.get("steps").get(0).get("waitingUntil").isNull()).isFalse();
        // the members got a notification
        await(() -> recipients().contains("bob@example.com"), () -> "the notification");
        assertThat(RecordingMailSender.SENT.getFirst().getSubject()).startsWith("Onay bekliyor: Approve Graph flow");

        long requestId = pendingRequest(BOB);
        // a card with "Onayla" / "Reddet" buttons that open the request with that decision (styles inlined)
        assertThat(RecordingMailSender.html(RecordingMailSender.SENT.getFirst())).contains(
                "/approvals?request=" + requestId + "&amp;decision=approve",
                "/approvals?request=" + requestId + "&amp;decision=reject", "Onayınız bekleniyor", "style=\"")
                .doesNotContain("<style");
        JsonNode forBob = json(mvc.get().uri("/api/approval-requests/" + requestId).with(BOB).exchange()).get("data");
        assertThat(forBob.get("canDecide").asBoolean()).isTrue();
        assertThat(forBob.get("source").get("type").asString()).isEqualTo("flow-step");
        assertThat(forBob.get("source").get("label").asString()).contains("#" + runId);
        assertThat(forBob.get("description").asString()).isEqualTo("Run " + runId + " needs a decision");
        // the person who started the run may not approve it, outsiders do not even see it
        assertThat(post("/api/approval-requests/" + requestId + "/approve", ADMIN, "{}"))
                .hasStatus(HttpStatus.FORBIDDEN).bodyJson().extractingPath("$.code").isEqualTo("SELF_APPROVAL_FORBIDDEN");
        assertThat(mvc.get().uri("/api/approval-requests/" + requestId).with(CAROL).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);

        assertThat(post("/api/approval-requests/" + requestId + "/approve", BOB, Map.of("comment", "Looks fine")))
                .hasStatusOk().bodyJson().extractingPath("$.data.status").isEqualTo("approved");
        JsonNode run = await(runId, "completed");

        assertThat(keys(run)).containsExactly("ask:completed:approved", "ok:completed");
        assertThat(run.get("steps").get(0).get("output").get("comment").asString()).isEqualTo("Looks fine");
        assertThat(recipients()).contains("ok@example.com").doesNotContain("no@example.com");
        assertThat(post("/api/approval-requests/" + requestId + "/reject", BOB, "{}"))
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("APPROVAL_ALREADY_DECIDED");
    }

    @Test
    void withoutEdgesARejectionOrATimeoutEndsTheRun() {
        long template = template();
        long group = group("bob");
        long flow = flow(List.of(approval("ask", group), mail("after", template, "after@example.com")), List.of());

        long rejected = start(flow);
        awaitWaiting(rejected);
        assertThat(post("/api/approval-requests/" + pendingRequest(BOB) + "/reject", BOB, Map.of("comment", "No")))
                .hasStatusOk();
        assertThat(keys(await(rejected, "completed"))).containsExactly("ask:completed:rejected");

        // the approval module owns the deadline: it times the request out and the step takes its timeout way
        long timedOut = start(flow);
        awaitWaiting(timedOut);
        jdbc.update("UPDATE approval_request SET due_at = ? WHERE status = 'PENDING'",
                Timestamp.from(Instant.now().minusSeconds(1)));
        JsonNode run = await(timedOut, "completed");
        assertThat(keys(run)).containsExactly("ask:completed:timeout");
        assertThat(requestStatus(timedOut)).isEqualTo("TIMED_OUT");
        assertThat(run.get("events").toString()).contains("approval_timed_out");

        // the engine's own deadline is only a safety net; when it fires, the request is withdrawn
        long safetyNet = start(flow);
        awaitWaiting(safetyNet);
        jdbc.update("UPDATE flow_step_run SET available_at = ? WHERE run_id = ? AND status = 'WAITING'",
                Timestamp.from(Instant.now().minusSeconds(1)), safetyNet);
        assertThat(keys(await(safetyNet, "completed"))).containsExactly("ask:completed:timeout");
        await(() -> "CANCELLED".equals(requestStatus(safetyNet)), () -> "the withdrawn request");
        assertThat(recipients()).doesNotContain("after@example.com");
    }

    @Test
    void cancellingAWaitingRunCancelsItsRequest() {
        long flow = flow(List.of(approval("ask", group("bob"))), List.of());
        long runId = start(flow);
        awaitWaiting(runId);

        assertThat(mvc.post().uri("/api/flow-runs/" + runId + "/cancel").with(ADMIN).exchange()).hasStatusOk()
                .bodyJson().extractingPath("$.data.status").isEqualTo("cancelled");
        await(() -> "CANCELLED".equals(requestStatus(runId)), () -> "the withdrawn request");
    }

    @Test
    void onlyAdminsManageGroupsAndMembersMustExist() {
        assertThat(post("/api/approval-groups", CAROL, Map.of("name", "Mine", "members", List.of())))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(post("/api/approval-groups", ADMIN, Map.of("name", "Ghosts",
                "members", List.of(Map.of("username", "nobody")))))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.errors[0].code")
                .isEqualTo("UNKNOWN_USER");
        long group = group("bob");
        flow(List.of(approval("ask", group)), List.of());
        assertThat(mvc.delete().uri("/api/approval-groups/" + group).with(ADMIN).exchange())
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("GROUP_IN_USE");
    }

    // ---- webhook trigger, filter and webhook steps ---------------------------------------------------------

    @Test
    void aSignedWebhookStartsARunThatFiltersAndCallsAnotherSystem() throws Exception {
        String target = "http://localhost:" + TARGET.getAddress().getPort() + "/hook";
        long flow = flow(List.of(
                        Map.of("type", "filter", "key", "big", "source", "trigger.orders",
                                "expression", Map.of(">", List.of(Map.of("var", "item.amount"), 100))),
                        Map.of("type", "webhook", "key", "notify", "url", target, "maxAttempts", 3,
                                "body", "{\"count\": {{steps.big.count}}, \"customer\": \"{{trigger.customer}}\"}")),
                List.of());
        MvcTestResult created = post("/api/flows/" + flow + "/triggers", ADMIN, Map.of("type", "webhook", "enabled", true,
                "filter", Map.of("==", List.of(Map.of("var", "trigger.type"), "order"))));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String url = json(created).get("data").get("trigger").get("webhookUrl").asString();
        String secret = json(created).get("data").get("secret").asString();
        assertThat(secret).hasSizeGreaterThan(30);

        byte[] body = """
                {"type": "order", "customer": "Ali \\"Veli\\" & Co", "orders": [{"amount": 50}, {"amount": 500}]}"""
                .getBytes(UTF_8);
        ANSWERS.add(500);   // the other system fails once: the step is retried
        MvcTestResult delivered = mvc.post().uri(url).header("X-Signature", sign(secret, body)).header("X-Event-Id", "e-1")
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
        assertThat(delivered).hasStatus(HttpStatus.ACCEPTED).bodyJson().extractingPath("$.data.status").isEqualTo("started");
        long runId = json(delivered).get("data").get("runId").asLong();

        // the same event again, a forged one, one the filter rejects
        assertThat(mvc.post().uri(url).header("X-Signature", sign(secret, body)).header("X-Event-Id", "e-1")
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange())
                .hasStatusOk().bodyJson().extractingPath("$.data.status").isEqualTo("duplicate");
        assertThat(mvc.post().uri(url).header("X-Signature", sign("wrong-secret", body))
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED).bodyJson().extractingPath("$.code").isEqualTo("INVALID_SIGNATURE");
        byte[] other = "{\"type\": \"refund\"}".getBytes(UTF_8);
        assertThat(mvc.post().uri(url).header("X-Signature", sign(secret, other))
                .contentType(MediaType.APPLICATION_JSON).content(other).exchange())
                .hasStatusOk().bodyJson().extractingPath("$.data.status").isEqualTo("filtered");

        JsonNode run = await(runId, "completed");
        assertThat(run.get("trigger").asString()).isEqualTo("webhook");
        assertThat(run.get("triggerPayload").get("customer").asString()).isEqualTo("Ali \"Veli\" & Co");
        assertThat(run.get("steps").get(0).get("output").get("count").asInt()).isEqualTo(1);
        JsonNode call = run.get("steps").get(1);
        assertThat(call.get("attempt").asInt()).isEqualTo(2);
        assertThat(call.get("output").get("body").get("ticket").asString()).isEqualTo("T-1");
        // the payload values were JSON-escaped into the template: the body is valid JSON with the original text
        JsonNode sent = JSON.readTree(RECEIVED.getLast());
        assertThat(sent.get("count").asInt()).isEqualTo(1);
        assertThat(sent.get("customer").asString()).isEqualTo("Ali \"Veli\" & Co");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flow_run WHERE flow_id = ?", Integer.class, flow)).isEqualTo(1);
    }

    @Test
    void webhookStepsMayOnlyCallAllowedHosts() {
        assertThat(post("/api/flows", ADMIN, Map.of("name", "Exfiltrate", "steps", List.of(Map.of("type", "webhook",
                "key", "out", "url", "https://evil.example.org/collect")))))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.errors[0].code")
                .isEqualTo("WEBHOOK_HOST_NOT_ALLOWED");
        assertThat(post("/api/flows", ADMIN, Map.of("name", "Kafka off", "steps", List.of(Map.of("type", "kafka",
                "key", "pub", "topic", "flows.orders", "payload", "{}")))))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.errors[0].code")
                .isEqualTo("KAFKA_DISABLED");
        assertThat(post("/api/flows", ADMIN, Map.of("name", "Bad rule", "steps", List.of(Map.of("type", "condition",
                "key", "c", "expression", Map.of("exec", List.of("x")))))))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.errors[0].code")
                .isEqualTo("INVALID_EXPRESSION");
    }
}
