package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.RecordingMailSender;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepHandler;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Two engines ("instances") on a real PostgreSQL with files in a real S3 API: the {@code SKIP LOCKED} claims, schedule
 * fires and file cleanup are exercised the way they run in production. Needs Docker; skipped without it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext   // stop this context's engine while the containers still run
class FlowPostgresClusterTest {

    // Started once and left running until the JVM exits (removed by Testcontainers then): the Spring context must be
    // able to shut down cleanly against them.
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
    static final GenericContainer<?> S3 = new GenericContainer<>("adobe/s3mock:3.12.0").withExposedPorts(9090);

    static {
        POSTGRES.start();
        S3.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/{vendor}");   // no demo data
        registry.add("storage.type", () -> "s3");
        registry.add("storage.s3.endpoint", () -> "http://" + S3.getHost() + ":" + S3.getMappedPort(9090));
        registry.add("storage.s3.bucket", () -> "cluster-test");
        registry.add("storage.s3.create-bucket", () -> "true");
        registry.add("storage.s3.access-key", () -> "test");
        registry.add("storage.s3.secret-key", () -> "test");
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final RequestPostProcessor ADMIN = user("admin").authorities(Arrays.stream(new String[]{
            "PLATFORM_USER", "PLATFORM_ADMIN", "SALARY_VIEW", "ALL_DEPARTMENTS"}).map(SimpleGrantedAuthority::new).toList());

    @Autowired MockMvcTester mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired FlowEngine nodeA;
    @Autowired RunStore store;
    @Autowired FlowRunRepository runs;
    @Autowired FlowScheduleRepository schedules;
    @Autowired ArtifactStore artifacts;
    @Autowired List<StepHandler> handlers;
    @Autowired FlowProperties properties;
    @Autowired Clock clock;

    FlowEngine nodeB;

    @BeforeEach
    void startSecondInstance() {
        RecordingMailSender.reset();
        nodeB = new FlowEngine(store, runs, schedules, artifacts, handlers, properties, clock, "node-b");
        nodeB.start();
    }

    @AfterEach
    void stopSecondInstance() {
        nodeB.stop();
        RecordingMailSender.reset();
    }

    private JsonNode post(String uri, String body) {
        MvcTestResult result = mvc.post().uri(uri).with(ADMIN).contentType(MediaType.APPLICATION_JSON).content(body)
                .exchange();
        assertThat(result.getResponse().getStatus()).isLessThan(300);
        return JSON.readTree(result.getResponse().getContentAsByteArray()).get("data");
    }

    private long mailOnlyFlow(String name) {
        long template = post("/api/mail-templates", """
                {"name": "%s", "subject": "Hi", "bodyHtml": "<p>Hi</p>"}""".formatted(name)).get("id").asLong();
        return post("/api/flows", """
                {"name": "%s", "steps": [{"type": "mail", "key": "mail", "templateId": %d, "to": ["a@example.com"]}]}"""
                .formatted(name, template)).get("id").asLong();
    }

    private long startRun(long flowId) {
        String location = mvc.post().uri("/api/flows/" + flowId + "/runs").with(ADMIN).exchange().getResponse()
                .getHeader(HttpHeaders.LOCATION);
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private static void await(BooleanSupplier condition, String what) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Timed out waiting for " + what);
            }
            Thread.sleep(100);
        }
    }

    @Test
    void twoInstancesShareTheQueueAndRunEveryStepExactlyOnce() throws InterruptedException {
        long flow = mailOnlyFlow("Shared queue");
        RecordingMailSender.delayMillis = 100;   // keeps both instances' slots busy
        List<Long> started = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            started.add(startRun(flow));
        }

        await(() -> jdbc.queryForObject("SELECT COUNT(*) FROM flow_run WHERE flow_id = ? AND status = 'COMPLETED'",
                Integer.class, flow) == 30, "30 completed runs");

        // no step was claimed twice: one attempt each, one mail each
        assertThat(jdbc.queryForObject("SELECT MAX(s.attempt) FROM flow_step_run s JOIN flow_run r ON r.id = s.run_id"
                + " WHERE r.flow_id = ?", Integer.class, flow)).isEqualTo(1);
        assertThat(RecordingMailSender.SENT).hasSize(30);
        // and both instances did some of the work
        Set<String> instances = jdbc.queryForList("SELECT e.message FROM flow_run_event e JOIN flow_run r"
                        + " ON r.id = e.run_id WHERE r.flow_id = ? AND e.type = 'step_started'", String.class, flow)
                .stream().map(message -> message.substring(message.lastIndexOf(" on ") + 4))
                .collect(Collectors.toSet());
        assertThat(instances).hasSize(2).contains("node-b");
    }

    @Test
    void aDueScheduleStartsOneRunWhenBothInstancesFireAtOnce() throws Exception {
        long flow = mailOnlyFlow("Fired once");
        long schedule = post("/api/flows/" + flow + "/schedules", """
                {"cron": "0 7 * * *", "timeZone": "Europe/Istanbul", "enabled": true}""").get("id").asLong();
        for (int round = 0; round < 5; round++) {
            jdbc.update("UPDATE flow_schedule SET next_fire_at = ? WHERE id = ?",
                    Timestamp.from(Instant.now().minusSeconds(1)), schedule);
            CompletableFuture.allOf(CompletableFuture.runAsync(nodeA::fireDueSchedules),
                    CompletableFuture.runAsync(nodeB::fireDueSchedules)).get();
            long runCount = jdbc.queryForObject("SELECT COUNT(*) FROM flow_run WHERE flow_id = ?", Long.class, flow);
            assertThat(runCount).isEqualTo(round + 1);
            await(() -> jdbc.queryForObject("SELECT COUNT(*) FROM flow_run WHERE flow_id = ? AND status = 'COMPLETED'",
                    Long.class, flow) == runCount, "the scheduled run to finish");   // or the next fire is skipped
        }
    }

    @Test
    void reportFilesGoToS3AreDownloadedThroughPresignedUrlsAndExpire() throws Exception {
        long flow = post("/api/flows", """
                {"name": "To S3", "steps": [{"type": "dataset", "key": "dataset", "fileName": "people.csv",
                  "definition": {"root": "Personnel", "columns": [{"field": "fullName", "label": "Name"}]}}]}""")
                .get("id").asLong();
        long runId = startRun(flow);
        await(() -> runs.findById(runId).orElseThrow().getStatus() == RunStatus.COMPLETED, "the run");
        RunArtifact file = artifacts.forRun(runId).getFirst();

        MvcTestResult download = mvc.get().uri("/api/flow-files/" + file.getId() + "/download").with(ADMIN).exchange();
        assertThat(download).hasStatus(HttpStatus.FOUND);
        URI presigned = URI.create(download.getResponse().getHeader(HttpHeaders.LOCATION));
        assertThat(presigned.getQuery()).contains("X-Amz-Signature");
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> object = http.send(HttpRequest.newBuilder(presigned).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(object.statusCode()).isEqualTo(200);
        assertThat(object.body()).startsWith("﻿Name");

        jdbc.update("UPDATE flow_artifact SET expires_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), file.getId());
        CompletableFuture.allOf(CompletableFuture.runAsync(nodeA::deleteExpiredFiles),
                CompletableFuture.runAsync(nodeB::deleteExpiredFiles)).get();

        assertThat(mvc.get().uri("/api/flow-files/" + file.getId() + "/download").with(ADMIN).exchange())
                .hasStatus(HttpStatus.GONE);
        assertThat(http.send(HttpRequest.newBuilder(presigned).build(), HttpResponse.BodyHandlers.discarding())
                .statusCode()).isEqualTo(404);
    }
}
