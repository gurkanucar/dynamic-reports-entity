package com.gucardev.dynamicreportsentity.platform.flow.core;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * One Kafka listener per topic that enabled triggers listen on, kept in line with the database every
 * {@code flows.kafka.reconcile-interval}. All instances join the same consumer group, so Kafka spreads the partitions
 * among them and each message is handled by one instance.
 * <p>
 * Delivery: a message's offset is committed after its runs were created (at least once). A redelivery after a crash
 * or rebalance is recognised by the inbox ({@code topic:partition:offset}) and starts nothing. A message that keeps
 * failing (database down) is retried with backoff for about a minute, then skipped and logged, so one bad message
 * cannot block its partition forever. A new consumer group starts at the latest offset: creating a trigger does not
 * replay the topic's history.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "flows.kafka.enabled", havingValue = "true")
class KafkaTriggerListeners implements SmartLifecycle {

    private final ConsumerFactory<String, String> consumers;
    private final FlowTriggerRepository triggers;
    private final TriggerService service;
    private final FlowProperties properties;
    private final Map<String, KafkaMessageListenerContainer<String, String>> containers = new ConcurrentHashMap<>();
    private ScheduledExecutorService timer;
    private volatile boolean running;

    KafkaTriggerListeners(ConsumerFactory<String, String> consumers, FlowTriggerRepository triggers,
                          TriggerService service, FlowProperties properties) {
        this.consumers = consumers;
        this.triggers = triggers;
        this.service = service;
        this.properties = properties;
    }

    @Override
    public synchronized void start() {
        running = true;
        timer = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("flow-kafka-triggers").daemon()
                .factory());
        long interval = properties.kafka().reconcileInterval().toMillis();
        timer.scheduleWithFixedDelay(this::reconcileSafely, 0, interval, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (timer != null) {
            timer.shutdownNow();
        }
        containers.values().forEach(KafkaMessageListenerContainer::stop);
        containers.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Starts listeners for new topics and stops those nobody listens on any more. */
    public synchronized void reconcile() {
        Set<String> wanted = new HashSet<>(triggers.findEnabledTopics(FlowTrigger.Type.KAFKA));
        wanted.removeIf(topic -> !properties.kafka().allows(topic));
        for (String topic : Set.copyOf(containers.keySet())) {
            if (!wanted.contains(topic)) {
                containers.remove(topic).stop();
                log.info("Stopped listening on Kafka topic {}", topic);
            }
        }
        for (String topic : wanted) {
            if (!containers.containsKey(topic) && running) {
                KafkaMessageListenerContainer<String, String> container = container(topic);
                container.start();
                containers.put(topic, container);
                log.info("Listening on Kafka topic {} (group {})", topic, properties.kafka().triggerGroupId());
            }
        }
    }

    Set<String> topics() {
        return Set.copyOf(containers.keySet());
    }

    /** Whether this instance got partitions of the topic assigned (listening has really begun). */
    boolean isAssigned(String topic) {
        KafkaMessageListenerContainer<String, String> container = containers.get(topic);
        return container != null && container.getAssignedPartitions() != null
                && !container.getAssignedPartitions().isEmpty();
    }

    private void reconcileSafely() {
        try {
            reconcile();
        } catch (RuntimeException e) {
            log.warn("Could not align the Kafka trigger listeners: {}", e.getMessage());
        }
    }

    private KafkaMessageListenerContainer<String, String> container(String topic) {
        ContainerProperties settings = new ContainerProperties(topic);
        settings.setGroupId(properties.kafka().triggerGroupId());
        settings.setAckMode(ContainerProperties.AckMode.RECORD);
        Properties overrides = new Properties();
        overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        overrides.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        settings.setKafkaConsumerProperties(overrides);
        settings.setMessageListener((MessageListener<String, String>) this::handle);
        KafkaMessageListenerContainer<String, String> container = new KafkaMessageListenerContainer<>(consumers, settings);
        ExponentialBackOff backOff = new ExponentialBackOff(1000, 2);
        backOff.setMaxElapsedTime(60_000);
        container.setCommonErrorHandler(new DefaultErrorHandler((record, error) -> log.error(
                "Gave up on Kafka message {}:{}:{} for flow triggers", record.topic(), record.partition(),
                record.offset(), error), backOff));
        container.setBeanName("flow-trigger-" + topic);
        return container;
    }

    private void handle(ConsumerRecord<String, String> record) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Header header : record.headers()) {
            headers.put(header.key(), header.value() == null ? null : new String(header.value(), UTF_8));
        }
        service.receiveKafka(record.topic(), record.partition(), record.offset(), record.key(), record.value(),
                headers);
    }
}
