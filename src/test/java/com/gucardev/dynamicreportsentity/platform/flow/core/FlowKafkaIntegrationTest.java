package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.RecordingMailSender;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/** Flows and a real Kafka broker: publishing from a step, starting runs from messages, redeliveries. */
// own in-memory database: this context is closed after the class, and create-drop would drop the shared one's tables
@SpringBootTest(properties = {"flows.kafka.enabled=true", "flows.kafka.allowed-topics=flows.*",
        "flows.kafka.reconcile-interval=1h", "spring.datasource.url=jdbc:h2:mem:kafka-test"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
class FlowKafkaIntegrationTest {

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final RequestPostProcessor ADMIN = user("admin").authorities(Arrays.stream(new String[]{
            "PLATFORM_USER", "PLATFORM_ADMIN", "ALL_DEPARTMENTS"}).map(SimpleGrantedAuthority::new).toList());

    @Autowired MockMvcTester mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired KafkaTriggerListeners listeners;
    @Autowired TriggerService triggers;

    @BeforeAll
    static void topics() throws Exception {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic("flows.orders", 2, (short) 1),
                    new NewTopic("flows.notifications", 1, (short) 1))).all().get();
        }
    }

    private JsonNode post(String uri, Object body) {
        MvcTestResult result = mvc.post().uri(uri).with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body)).exchange();
        assertThat(result.getResponse().getStatus()).as(new String(result.getResponse().getContentAsByteArray(), UTF_8))
                .isLessThan(300);
        return JSON.readTree(result.getResponse().getContentAsByteArray()).get("data");
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

    private String runStatus(String runId) {
        return jdbc.queryForObject("SELECT status FROM flow_run WHERE id = ?", String.class, runId);
    }

    @Test
    void aStepPublishesTheRenderedEvent() throws Exception {
        RecordingMailSender.reset();
        String flow = post("/api/flows", Map.of("name", "Publish", "steps", List.of(Map.of("type", "kafka", "key", "pub",
                "topic", "flows.notifications", "messageKey", "run-{{run.id}}",
                "payload", "{\"flow\": \"{{flow.name}}\", \"run\": \"{{run.id}}\"}")))).get("id").asString();
        String runId = post("/api/flows/" + flow + "/runs", Map.of()).get("id").asString();
        await(() -> "COMPLETED".equals(runStatus(runId)), "the run");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-reader", ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("flows.notifications"));
            ConsumerRecord<String, String> found = null;
            Instant deadline = Instant.now().plusSeconds(20);
            while (found == null && Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (("run-" + runId).equals(record.key())) {
                        found = record;
                    }
                }
            }
            assertThat(found).isNotNull();
            assertThat(JSON.readTree(found.value()).get("run").asString()).isEqualTo(runId);
            assertThat(JSON.readTree(found.value()).get("flow").asString()).isEqualTo("Publish");
            assertThat(new String(found.headers().lastHeader("flow-run-id").value(), UTF_8)).isEqualTo(String.valueOf(runId));
        }
    }

    @Test
    void messagesStartRunsOncePerMessageAndOnlyWhenTheFilterMatches() throws Exception {
        String flow = post("/api/flows", Map.of("name", "On order", "steps", List.of(Map.of("type", "condition",
                "key", "big", "expression", Map.of(">", List.of(Map.of("var", "trigger.value.amount"), 100))))))
                .get("id").asString();
        JsonNode trigger = post("/api/flows/" + flow + "/triggers", Map.of("type", "kafka", "enabled", true,
                "topic", "flows.orders", "filter", Map.of("==", List.of(Map.of("var", "trigger.value.type"), "order"))))
                .get("trigger");
        assertThat(trigger.get("topic").asString()).isEqualTo("flows.orders");
        listeners.reconcile();
        await(() -> listeners.isAssigned("flows.orders"), "partitions of flows.orders");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()),
                new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>("flows.orders", "o-1", "{\"type\": \"order\", \"amount\": 500}")).get();
            producer.send(new ProducerRecord<>("flows.orders", "r-1", "{\"type\": \"refund\", \"amount\": 50}")).get();
        }
        await(() -> jdbc.queryForObject("SELECT COUNT(*) FROM flow_run WHERE flow_id = ? AND status = 'COMPLETED'",
                Integer.class, flow) == 1, "the run of the order");
        Thread.sleep(1000);   // the refund was filtered out: still one run
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flow_run WHERE flow_id = ?", Integer.class, flow)).isEqualTo(1);
        String outcome = jdbc.queryForObject("SELECT s.outcome FROM flow_step_run s JOIN flow_run r ON r.id = s.run_id"
                + " WHERE r.flow_id = ?", String.class, flow);
        assertThat(outcome).isEqualTo("true");
        assertThat(jdbc.queryForObject("SELECT triggered_by FROM flow_run WHERE flow_id = ?", String.class, flow))
                .isEqualTo("kafka:" + trigger.get("id").asString());

        // a redelivery of a message already handled (after a rebalance) starts nothing
        Map<String, Object> event = jdbc.queryForMap("SELECT event_key FROM flow_trigger_event WHERE trigger_id = ?",
                trigger.get("id").asString());
        String[] position = ((String) event.get("event_key")).split(":");
        triggers.receiveKafka(position[0], Integer.parseInt(position[1]), Long.parseLong(position[2]), "o-1",
                "{\"type\": \"order\", \"amount\": 500}", Map.of());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flow_run WHERE flow_id = ?", Integer.class, flow)).isEqualTo(1);
    }

    @Test
    void topicsOutsideTheAllowListAreRejected() {
        String flow = post("/api/flows", Map.of("name", "Elsewhere", "steps", List.of(Map.of("type", "condition",
                "key", "c", "expression", true)))).get("id").asString();
        assertThat(mvc.post().uri("/api/flows/" + flow + "/triggers").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"kafka\", \"enabled\": true, \"topic\": \"payments.internal\"}").exchange())
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.errors[0].code")
                .isEqualTo("TOPIC_NOT_ALLOWED");
    }
}
