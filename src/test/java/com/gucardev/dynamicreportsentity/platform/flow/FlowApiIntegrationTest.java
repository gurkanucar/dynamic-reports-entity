package com.gucardev.dynamicreportsentity.platform.flow;

import com.gucardev.dynamicreportsentity.platform.flow.core.FlowEngine;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Flows end to end on the demo data: the API, the engine (polling, claims, retries, cancel, lease takeover),
 * schedules and e-mails ({@link RecordingMailSender} instead of SMTP).
 */
@SpringBootTest
@AutoConfigureMockMvc
class FlowApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration WAIT = Duration.ofSeconds(20);

    /** Users as the real user store knows them (runs act as the flow owner, looked up there). */
    private static final RequestPostProcessor ADMIN = as("admin", "PLATFORM_USER", "PLATFORM_ADMIN", "SALARY_VIEW",
            "NATIONAL_ID_VIEW", "ALL_DEPARTMENTS");
    private static final RequestPostProcessor CAROL = as("carol", "PLATFORM_USER");
    private static final RequestPostProcessor ALICE = as("alice", "PLATFORM_USER");

    private static final String HEADCOUNT = """
            {"root": "Personnel",
             "columns": [{"field": "department.name", "label": "Department"}, {"aggregate": "COUNT", "label": "Headcount"}],
             "sort": [{"field": "department.name"}]}""";

    @Autowired
    MockMvcTester mvc;
    @Autowired
    FlowEngine engine;
    @Autowired
    JdbcTemplate jdbc;

    private static RequestPostProcessor as(String name, String... authorities) {
        return user(name).authorities(Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    @BeforeEach
    void setUp() {
        RecordingMailSender.reset();
    }

    @AfterEach
    void tearDown() {
        RecordingMailSender.reset();   // releases blocked sends
    }

    // ---- helpers --------------------------------------------------------------------------------------

    private static JsonNode json(MvcTestResult result) {
        return JSON.readTree(result.getResponse().getContentAsByteArray());
    }

    private MvcTestResult post(String uri, RequestPostProcessor user, String body) {
        return mvc.post().uri(uri).with(user).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private String createTemplate(String name, String subject, String body) {
        MvcTestResult result = post("/api/mail-templates", ADMIN, JSON.writeValueAsString(Map.of(
                "name", name, "subject", subject, "bodyHtml", body)));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return json(result).get("data").get("id").asString();
    }

    private String datasetTemplate(String name) {
        return createTemplate(name, "{{flow.name}} ({{steps.dataset.rowCount}} rows)", """
                <h1>{{flow.name}}</h1>
                <table>{{#steps.dataset.rows}}<tr>{{#cells}}<td>{{.}}</td>{{/cells}}</tr>{{/steps.dataset.rows}}</table>""");
    }

    private String datasetAndMail(String name, String templateId, String recipient) {
        return """
                {"name": "%s",
                 "steps": [
                   {"type": "dataset", "key": "dataset", "definition": %s, "fileName": "headcount.csv"},
                   {"type": "mail", "key": "mail", "templateId": "%s", "to": ["%s"], "attachments": ["dataset"]}
                 ]}""".formatted(name, HEADCOUNT, templateId, recipient);
    }

    private String mailOnly(String name, String templateId, Integer maxAttempts) {
        return """
                {"name": "%s",
                 "steps": [{"type": "mail", "key": "mail", "templateId": "%s", "to": ["team@example.com"], "maxAttempts": %s}]}
                """.formatted(name, templateId, maxAttempts);
    }

    private String createFlow(RequestPostProcessor user, String body) {
        MvcTestResult result = post("/api/flows", user, body);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return json(result).get("data").get("id").asString();
    }

    private String start(RequestPostProcessor user, String flowId) {
        MvcTestResult result = mvc.post().uri("/api/flows/" + flowId + "/runs").with(user).exchange();
        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).startsWith("/api/flow-runs/");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private JsonNode run(RequestPostProcessor user, String runId) {
        return json(mvc.get().uri("/api/flow-runs/" + runId).with(user).exchange()).get("data");
    }

    private JsonNode awaitRun(RequestPostProcessor user, String runId, String... statuses) {
        Set<String> wanted = Set.of(statuses);
        JsonNode[] last = new JsonNode[1];
        await(() -> wanted.contains((last[0] = run(user, runId)).get("status").asString()),
                () -> "run " + runId + " to become " + wanted + ", last: " + last[0]);
        return last[0];
    }

    private static void await(BooleanSupplier condition, java.util.function.Supplier<String> what) {
        Instant deadline = Instant.now().plus(WAIT);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Timed out waiting for " + what.get());
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    private static List<String> eventTypes(JsonNode run) {
        List<String> types = new ArrayList<>();
        run.get("events").forEach(event -> types.add(event.get("type").asString()));
        return types;
    }

    private static MimeMessage mailTo(String address) {
        return RecordingMailSender.SENT.stream().filter(message -> {
            try {
                return Arrays.stream(message.getRecipients(Message.RecipientType.TO))
                        .anyMatch(recipient -> recipient.toString().equals(address));
            } catch (jakarta.mail.MessagingException e) {
                throw new IllegalStateException(e);
            }
        }).findFirst().orElseThrow(() -> new AssertionError("No mail to " + address));
    }

    // ---- the happy path -------------------------------------------------------------------------------

    @Test
    void scheduledDatasetIsRunAndMailedWithTheCsvAttached() throws Exception {
        String template = datasetTemplate("Headcount mail");
        String flow = createFlow(ADMIN, datasetAndMail("Weekly headcount", template, "hr@example.com"));

        String runId = start(ADMIN, flow);
        JsonNode run = awaitRun(ADMIN, runId, "completed");

        assertThat(run.get("trigger").asString()).isEqualTo("manual");
        assertThat(run.get("runAs").get("username").asString()).isEqualTo("admin");
        assertThat(run.get("steps")).hasSize(2);
        JsonNode dataset = run.get("steps").get(0);
        assertThat(dataset.get("status").asString()).isEqualTo("completed");
        assertThat(dataset.get("output").get("rowCount").asInt()).isEqualTo(7);
        assertThat(dataset.get("output").get("columns").get(0).asString()).isEqualTo("Department");
        assertThat(eventTypes(run)).containsSubsequence("run_queued", "step_started", "step_completed",
                "step_started", "step_completed", "run_completed");

        MimeMessage mail = mailTo("hr@example.com");
        assertThat(mail.getSubject()).isEqualTo("Weekly headcount (7 rows)");
        assertThat(RecordingMailSender.html(mail)).contains("<h1>Weekly headcount</h1>", "<td>Finance</td>");
        String csv = RecordingMailSender.attachments(mail).get("headcount.csv");
        assertThat(csv).startsWith("﻿Department,Headcount\r\n").contains("Software Development,");

        // the file can be downloaded from the run
        JsonNode file = run.get("files").get(0);
        assertThat(file.get("fileName").asString()).isEqualTo("headcount.csv");
        MvcTestResult download = mvc.get().uri(file.get("downloadUrl").asString()).with(ADMIN).exchange();
        assertThat(download).hasStatusOk();
        assertThat(download.getResponse().getContentAsString(UTF_8)).isEqualTo(csv);
        assertThat(mvc.get().uri(file.get("downloadUrl").asString()).with(ALICE).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);

        // what was sent is kept with the run, to be shown in the page
        String body = run.get("steps").get(1).get("output").get("bodyFileId").asString();
        MvcTestResult content = mvc.get().uri("/api/flow-files/" + body + "/content").with(ADMIN).exchange();
        assertThat(content).hasStatusOk();
        assertThat(json(content).get("data").get("content").asString()).isEqualTo(RecordingMailSender.html(mail));
        assertThat(mvc.get().uri("/api/flow-files/" + file.get("id").asString() + "/content").with(ADMIN).exchange())
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void excelFilesAreAttachedAndTheMailShowsAsManyRowsAsTheStepAllows() throws Exception {
        String template = datasetTemplate("Excel mail");
        String flow = createFlow(ADMIN, """
                {"name": "Excel headcount",
                 "steps": [
                   {"type": "dataset", "key": "dataset", "definition": %s, "format": "xlsx", "mailRows": 3},
                   {"type": "mail", "key": "mail", "templateId": "%s", "to": ["excel@example.com"], "attachments": ["dataset"]}
                 ]}""".formatted(HEADCOUNT, template));

        JsonNode run = awaitRun(ADMIN, start(ADMIN, flow), "completed");

        JsonNode output = run.get("steps").get(0).get("output");
        assertThat(output.get("fileName").asString()).isEqualTo("dataset.xlsx");
        assertThat(output.get("rows")).hasSize(3);
        assertThat(output.get("previewTruncated").asBoolean()).isTrue();
        MimeMessage mail = mailTo("excel@example.com");
        byte[] excel = RecordingMailSender.attachmentBytes(mail).get("dataset.xlsx");
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(excel))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(7);   // header + all 7 departments
        }
        assertThat(mail.getContent().toString()).isNotNull();
        assertThat(post("/api/flows", ADMIN, """
                {"name": "Wrong extension", "steps": [{"type": "dataset", "key": "dataset", "definition": %s,
                 "format": "xlsx", "fileName": "x.csv"}]}""".formatted(HEADCOUNT)))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyText().contains("FILE_EXTENSION_MISMATCH");
    }

    @Test
    void styleBlocksOfTemplatesAreInlinedForMailClientsThatDropThem() throws Exception {
        String template = createTemplate("Styled", "Styled", """
                <style>h1 { color: #dc2626; font-size: 20px } .data td { padding: 8px } a:hover { color: red }</style>
                <h1 style="font-size: 18px">{{flow.name}}</h1><table class="data"><tr><td>x</td></tr></table>""");
        String flow = createFlow(ADMIN, mailOnly("Styled mail", template, null));

        awaitRun(ADMIN, start(ADMIN, flow), "completed");

        String html = RecordingMailSender.html(mailTo("team@example.com"));
        assertThat(html).contains("<h1 style=\"color: #dc2626; font-size: 18px;\">Styled mail</h1>",
                "<td style=\"padding: 8px;\">x</td>", "a:hover { color: red }");
    }

    @Test
    void datasetsRunWithTheOwnersRowSecurity() {
        String template = datasetTemplate("Carol's mail");
        String flow = createFlow(CAROL, datasetAndMail("My department", template, "carol@example.com"));

        JsonNode run = awaitRun(CAROL, start(CAROL, flow), "completed");

        assertThat(run.get("steps").get(0).get("output").get("rowCount").asInt()).isEqualTo(1);
        assertThat(RecordingMailSender.html(mailTo("carol@example.com")))
                .contains("<td>Software Development</td>").doesNotContain("Finance");
    }

    // ---- retries, failures, cancel, takeover ------------------------------------------------------------

    @Test
    void transientMailErrorsAreRetried() {
        String flow = createFlow(ADMIN, mailOnly("Retried", createTemplate("Retry", "Hi", "<p>Hi</p>"), 3));
        RecordingMailSender.FAILURES.set(2);

        JsonNode run = awaitRun(ADMIN, start(ADMIN, flow), "completed");

        JsonNode step = run.get("steps").get(0);
        assertThat(step.get("attempt").asInt()).isEqualTo(3);
        assertThat(eventTypes(run).stream().filter("step_retry_scheduled"::equals)).hasSize(2);
        assertThat(RecordingMailSender.SENT).hasSize(1);
    }

    @Test
    void runFailsWhenAttemptsAreUsedUp() {
        String flow = createFlow(ADMIN, mailOnly("Failing", createTemplate("Fail", "Hi", "<p>Hi</p>"), 2));
        RecordingMailSender.FAILURES.set(10);

        JsonNode run = awaitRun(ADMIN, start(ADMIN, flow), "failed");

        assertThat(run.get("error").get("code").asString()).isEqualTo("MAIL_SERVER_UNAVAILABLE");
        assertThat(run.get("steps").get(0).get("attempt").asInt()).isEqualTo(2);
        assertThat(eventTypes(run)).endsWith("step_failed", "run_failed");
    }

    @Test
    void cancelStopsARunningStep() {
        String flow = createFlow(ADMIN, mailOnly("Cancelled", createTemplate("Cancel", "Hi", "<p>Hi</p>"), null));
        RecordingMailSender.block = true;
        String runId = start(ADMIN, flow);
        await(() -> RecordingMailSender.BLOCKED.get() > 0, () -> "the mail step to start");

        MvcTestResult cancel = mvc.post().uri("/api/flow-runs/" + runId + "/cancel").with(ADMIN).exchange();
        assertThat(cancel).hasStatusOk();
        assertThat(json(cancel).get("data").get("cancelRequested").asBoolean()).isTrue();

        JsonNode run = awaitRun(ADMIN, runId, "cancelled");
        assertThat(run.get("steps").get(0).get("status").asString()).isEqualTo("cancelled");
        assertThat(RecordingMailSender.SENT).isEmpty();
        assertThat(mvc.post().uri("/api/flow-runs/" + runId + "/cancel").with(ADMIN).exchange())
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("RUN_ALREADY_FINISHED");
    }

    @Test
    void aStepWhoseLeaseExpiresIsTakenOverAndRetried() {
        String flow = createFlow(ADMIN, mailOnly("Taken over", createTemplate("Takeover", "Hi", "<p>Hi</p>"), 3));
        RecordingMailSender.block = true;
        String runId = start(ADMIN, flow);
        await(() -> RecordingMailSender.BLOCKED.get() > 0, () -> "the mail step to start");

        // the instance "dies": its lease runs out and somebody else owns the claim now
        jdbc.update("UPDATE flow_step_run SET owner = 'dead-node#1', lease_until = ? WHERE run_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)), runId);
        RecordingMailSender.block = false;

        JsonNode run = awaitRun(ADMIN, runId, "completed");
        assertThat(run.get("steps").get(0).get("attempt").asInt()).isEqualTo(2);
        assertThat(run.get("events").toString()).contains("STEP_LEASE_EXPIRED");
        // once in total: either the old attempt was stopped before sending, or it sent and recorded the
        // delivery, which keeps the new attempt from sending again
        assertThat(RecordingMailSender.SENT).hasSize(1);
    }

    @Test
    void runsWaitForAFreeSlotInsteadOfBeingRejected() throws InterruptedException {
        String flow = createFlow(ADMIN, mailOnly("Queued", createTemplate("Queue", "Hi", "<p>Hi</p>"), null));
        RecordingMailSender.block = true;
        List<String> runs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            runs.add(start(ADMIN, flow));   // never rejected
        }
        await(() -> RecordingMailSender.BLOCKED.get() >= 2, () -> "mail steps to start");
        Thread.sleep(500);

        // 2 light slots per instance (flows.light-slots in the tests): the rest waits in the table
        assertThat(RecordingMailSender.BLOCKED.get()).isLessThan(8);
        assertThat(runs.stream().filter(runId -> run(ADMIN, runId).get("status").asString().equals("queued")))
                .isNotEmpty();

        RecordingMailSender.block = false;
        runs.forEach(runId -> awaitRun(ADMIN, runId, "completed"));
        assertThat(RecordingMailSender.SENT).hasSize(8);
    }

    @Test
    void filesAreDeletedAfterTheRetention() {
        String flow = createFlow(ADMIN, datasetAndMail("Kept a while", datasetTemplate("Retention"), "keep@example.com"));
        JsonNode run = awaitRun(ADMIN, start(ADMIN, flow), "completed");
        JsonNode file = run.get("files").get(0);
        String fileId = file.get("id").asString();
        assertThat(file.get("expired").asBoolean()).isFalse();
        assertThat(Instant.parse(file.get("expiresAt").asString())).isAfter(Instant.now().plus(Duration.ofDays(6)));
        java.nio.file.Path stored = java.nio.file.Path.of("target/test-files").resolve(
                jdbc.queryForObject("SELECT storage_key FROM flow_artifact WHERE id = ?", String.class, fileId));
        assertThat(stored).exists();

        jdbc.update("UPDATE flow_artifact SET expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), fileId);
        engine.deleteExpiredFiles();

        assertThat(stored).doesNotExist();
        assertThat(mvc.get().uri("/api/flow-files/" + fileId + "/download").with(ADMIN).exchange())
                .hasStatus(HttpStatus.GONE).bodyJson().extractingPath("$.code").isEqualTo("FILE_EXPIRED");
        JsonNode after = run(ADMIN, run.get("id").asString()).get("files").get(0);
        assertThat(after.get("expired").asBoolean()).isTrue();
        assertThat(after.get("downloadUrl").isNull()).isTrue();
    }

    // ---- schedules ------------------------------------------------------------------------------------

    @Test
    void dueSchedulesStartOneRunPerFireTime() {
        String flow = createFlow(ADMIN, datasetAndMail("Daily", datasetTemplate("Daily mail"), "daily@example.com"));
        MvcTestResult created = post("/api/flows/" + flow + "/schedules", ADMIN,
                """
                {"cron": "0 7 * * MON-FRI", "timeZone": "Europe/Istanbul", "enabled": true}""");
        assertThat(created).hasStatus(HttpStatus.CREATED);
        JsonNode schedule = json(created).get("data");
        String scheduleId = schedule.get("id").asString();
        Instant firstFire = Instant.parse(schedule.get("nextFireAt").asString());
        assertThat(firstFire.atZone(java.time.ZoneId.of("Europe/Istanbul")).getHour()).isEqualTo(7);

        // pretend the fire time has come
        jdbc.update("UPDATE flow_schedule SET next_fire_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), scheduleId);
        engine.fireDueSchedules();
        engine.fireDueSchedules();   // already moved on: no second run

        JsonNode runs = json(mvc.get().uri("/api/flows/" + flow + "/runs").with(ADMIN).exchange());
        assertThat(runs.get("data")).hasSize(1);
        assertThat(runs.get("meta").get("totalItems").asInt()).isEqualTo(1);
        JsonNode run = runs.get("data").get(0);
        assertThat(run.get("trigger").asString()).isEqualTo("schedule");
        assertThat(run.get("scheduleId").asString()).isEqualTo(scheduleId);
        awaitRun(ADMIN, run.get("id").asString(), "completed");

        JsonNode after = json(mvc.get().uri("/api/flow-schedules/" + scheduleId).with(ADMIN).exchange()).get("data");
        assertThat(after.get("lastRunId").asString()).isEqualTo(run.get("id").asString());
        assertThat(Instant.parse(after.get("nextFireAt").asString())).isAfter(Instant.now());
    }

    @Test
    void schedulesAreValidated() {
        String flow = createFlow(ADMIN, mailOnly("Scheduled", createTemplate("Sched", "Hi", "<p>Hi</p>"), null));
        String uri = "/api/flows/" + flow + "/schedules";

        assertThat(post(uri, ADMIN, """
                {"cron": "* * * * *", "timeZone": "UTC", "enabled": true}"""))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.errors[0].code").isEqualTo("SCHEDULE_TOO_FREQUENT");
        assertThat(post(uri, ADMIN, """
                {"cron": "0 7 * *", "timeZone": "Mars/Base", "enabled": true}"""))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.errors[*].code").asArray()
                .containsExactlyInAnyOrder("INVALID_TIME_ZONE", "INVALID_CRON");

        MvcTestResult disabled = post(uri, ADMIN, """
                {"cron": "@daily", "timeZone": "UTC", "enabled": false}""");
        assertThat(disabled).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.data.nextFireAt").isNull();
    }

    // ---- definitions, permissions, versions ---------------------------------------------------------------

    @Test
    void flowsAreValidatedWhenSaved() {
        String template = createTemplate("Validation", "Hi", "<p>Hi</p>");

        assertThat(post("/api/flows", ADMIN, """
                {"name": "x", "steps": [{"type": "ftp", "key": "a"}]}"""))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("UNKNOWN_STEP_TYPE");
        assertThat(post("/api/flows", ADMIN, """
                {"steps": []}"""))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.errors[*].field").asArray().contains("name", "steps");
        assertThat(post("/api/flows", ADMIN, """
                {"name": "x", "steps": [
                  {"type": "mail", "key": "mail", "templateId": "%s", "to": ["boss@gmail.com"], "attachments": ["dataset"]},
                  {"type": "mail", "key": "mail", "templateId": "%s", "to": ["a@example.com"]}]}""".formatted(template, java.util.UUID.randomUUID())))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.errors[*].code").asArray().containsExactly(
                        "RECIPIENT_NOT_ALLOWED", "UNKNOWN_ATTACHMENT", "DUPLICATE_STEP_KEY", "TEMPLATE_NOT_FOUND");
        assertThat(post("/api/flows", ADMIN, """
                {"name": "x", "steps": [{"type": "dataset", "key": "r", "definition": {"root": "Personnel", "columns": [{"field": "nope"}]}}]}"""))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.errors[0].code").isEqualTo("INVALID_DATASET_DEFINITION");
        // alice may not use salaries, so she may not schedule a dataset on them either
        assertThat(post("/api/flows", ALICE, """
                {"name": "x", "steps": [{"type": "dataset", "key": "r", "definition": {"root": "Personnel", "columns": [{"field": "salary"}]}}]}"""))
                .hasStatus(HttpStatus.FORBIDDEN).bodyJson().extractingPath("$.code").isEqualTo("DATASET_FIELD_FORBIDDEN");
    }

    @Test
    void problemsCarryCodeAndTraceId() {
        MvcTestResult missing = mvc.get().uri("/api/flows/" + java.util.UUID.randomUUID()).with(ADMIN).header("X-Request-Id", "req-42")
                .exchange();
        assertThat(missing).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(json(missing).get("code").asString()).isEqualTo("FLOW_NOT_FOUND");
        assertThat(json(missing).get("traceId").asString()).isEqualTo("req-42");
        assertThat(mvc.get().uri("/api/flows?size=500").with(ADMIN).exchange()).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_PAGINATION");
    }

    @Test
    void usersOnlySeeTheirOwnFlows() {
        String flow = createFlow(CAROL, mailOnly("Carol's", createTemplate("Private", "Hi", "<p>Hi</p>"), null));

        assertThat(mvc.get().uri("/api/flows/" + flow).with(ALICE).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.post().uri("/api/flows/" + flow + "/runs").with(ALICE).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/api/flows/" + flow).with(ADMIN).exchange()).hasStatusOk();
        assertThat(json(mvc.get().uri("/api/flows?size=100").with(ALICE).exchange()).get("data").toString())
                .doesNotContain("Carol's");
    }

    @Test
    void updatesNeedTheCurrentVersion() {
        String template = createTemplate("Versioned", "Hi", "<p>Hi</p>");
        String flow = createFlow(ADMIN, mailOnly("Versioned", template, null));
        MvcTestResult current = mvc.get().uri("/api/flows/" + flow).with(ADMIN).exchange();
        String etag = current.getResponse().getHeader(HttpHeaders.ETAG);
        String changed = mailOnly("Renamed", template, 2);

        assertThat(mvc.put().uri("/api/flows/" + flow).with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content(changed).exchange())
                .hasStatus(HttpStatus.PRECONDITION_REQUIRED).bodyJson().extractingPath("$.code")
                .isEqualTo("IF_MATCH_REQUIRED");
        MvcTestResult updated = mvc.put().uri("/api/flows/" + flow).with(ADMIN).header(HttpHeaders.IF_MATCH, etag)
                .contentType(MediaType.APPLICATION_JSON).content(changed).exchange();
        assertThat(updated).hasStatusOk().bodyJson().extractingPath("$.data.name").isEqualTo("Renamed");
        assertThat(updated.getResponse().getHeader(HttpHeaders.ETAG)).isNotEqualTo(etag);
        assertThat(mvc.put().uri("/api/flows/" + flow).with(ADMIN).header(HttpHeaders.IF_MATCH, etag)
                .contentType(MediaType.APPLICATION_JSON).content(changed).exchange())
                .hasStatus(HttpStatus.PRECONDITION_FAILED).bodyJson().extractingPath("$.code")
                .isEqualTo("VERSION_MISMATCH");
    }

    // ---- mail templates -------------------------------------------------------------------------------

    @Test
    void templatesAreCheckedEscapedAndCannotReachJavaObjects() {
        assertThat(post("/api/mail-templates", ADMIN, """
                {"name": "Broken", "subject": "Hi", "bodyHtml": "{{#steps}}never closed"}"""))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.errors[0].code").isEqualTo("TEMPLATE_SYNTAX_ERROR");

        MvcTestResult preview = post("/api/mail-template-previews", ADMIN, """
                {"subject": "{{flow.name}}", "bodyHtml": "<b>{{flow.name}}</b>|{{flow.name.class}}|{{flow.name.bytes}}",
                 "model": {"flow": {"name": "<script>x</script>"}}}""");
        assertThat(preview).hasStatusOk();
        assertThat(json(preview).get("data").get("bodyHtml").asString())
                .isEqualTo("<b>&lt;script&gt;x&lt;/script&gt;</b>||");
        assertThat(json(preview).get("data").get("subject").asString()).isEqualTo("<script>x</script>");
    }

    @Test
    void templatesInUseCannotBeDeleted() {
        String template = createTemplate("In use", "Hi", "<p>Hi</p>");
        String flow = createFlow(ADMIN, mailOnly("Uses it", template, null));

        assertThat(mvc.delete().uri("/api/mail-templates/" + template).with(ADMIN).exchange())
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("TEMPLATE_IN_USE");
        assertThat(mvc.delete().uri("/api/mail-templates/" + template).with(CAROL).exchange())
                .hasStatus(HttpStatus.FORBIDDEN);

        assertThat(mvc.delete().uri("/api/flows/" + flow).with(ADMIN).exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.delete().uri("/api/mail-templates/" + template).with(ADMIN).exchange())
                .hasStatus(HttpStatus.NO_CONTENT);
    }
}
