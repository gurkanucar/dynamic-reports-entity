package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.spi.StepResult;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepException;
import com.gucardev.dynamicreportsentity.platform.flow.spi.RunData;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepHandler;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepContext;
import com.gucardev.dynamicreportsentity.platform.notification.TemplateRenderer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Publishes the rendered payload and waits for the broker's acknowledgement (so a step counts as done only when the
 * event is stored). Headers {@code flow-run-id} and {@code flow-step} let consumers drop duplicates of a retry.
 */
@Component
@ConditionalOnProperty(name = "flows.kafka.enabled", havingValue = "true")
class KafkaPublishStepHandler implements StepHandler {

    private final KafkaTemplate<String, String> kafka;
    private final TemplateRenderer renderer;
    private final FlowProperties properties;
    private final Clock clock;

    KafkaPublishStepHandler(KafkaTemplate<String, String> kafka, TemplateRenderer renderer, FlowProperties properties,
                            Clock clock) {
        this.kafka = kafka;
        this.renderer = renderer;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String type() {
        return KafkaPublishStep.TYPE;
    }

    @Override
    public StepResult execute(StepContext context) throws InterruptedException {
        KafkaPublishStep step = (KafkaPublishStep) context.step().step();
        if (!properties.kafka().allows(step.topic())) {
            throw new StepException("TOPIC_NOT_ALLOWED", "Publishing to " + step.topic() + " is not allowed", false);
        }
        Map<String, Object> data = RunData.of(context.step(), clock);
        String key = step.messageKey() == null ? null : renderer.renderText(step.messageKey(), data);
        ProducerRecord<String, String> record = new ProducerRecord<>(step.topic(), key == null || key.isEmpty() ? null : key,
                renderer.renderJson(step.payload(), data));
        record.headers().add("flow-run-id", String.valueOf(context.step().runId()).getBytes(UTF_8));
        record.headers().add("flow-step", step.key().getBytes(UTF_8));
        Duration timeout = Duration.between(clock.instant(), context.deadline());
        RecordMetadata sent;
        try {
            sent = kafka.send(record).get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS).getRecordMetadata();
        } catch (TimeoutException e) {
            throw new StepException("KAFKA_UNAVAILABLE", "The broker did not acknowledge in time", true);
        } catch (ExecutionException e) {
            throw new StepException("KAFKA_UNAVAILABLE", "Publishing failed: " + e.getCause().getMessage(), true);
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("topic", sent.topic());
        output.put("partition", sent.partition());
        output.put("offset", sent.offset());
        output.put("key", key);
        return StepResult.done(output);
    }
}
