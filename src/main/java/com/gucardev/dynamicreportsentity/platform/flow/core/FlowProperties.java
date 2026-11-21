package com.gucardev.dynamicreportsentity.platform.flow.core;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.List;

/**
 * Settings of the flow engine ({@code flows.*}).
 *
 * @param pollInterval        how often each instance looks for ready steps (a local start or finish also
 *                            triggers a look right away)
 * @param scheduleInterval    how often each instance looks for due schedules
 * @param lease               a claimed step belongs to its instance this long without renewal (renewed every
 *                            quarter of it); a dead instance's steps are retried elsewhere after it
 * @param heavySlots          report steps running at the same time per instance; keep below the JDBC pool size
 * @param lightSlots          e-mail steps running at the same time per instance
 * @param reportTimeout       default timeout of a report step
 * @param lightTimeout        default timeout of an e-mail step
 * @param fileRetention       files of runs are deleted this long after they were made (downloads then answer 410)
 * @param cleanupInterval     how often each instance deletes expired files
 * @param maxAttempts         default attempts per step (first try included)
 * @param retryBackoff        wait before the 2nd attempt; doubles for each further attempt (max 30 minutes)
 * @param maxSchedulesPerFlow  cron schedules per flow
 * @param minScheduleInterval  schedules may not fire more often than this
 * @param shutdownGrace        on shutdown, running steps are stopped and handed back after at most this long
 * @param webhook             outgoing and incoming webhooks
 * @param kafka               Kafka triggers and publishing
 */
@ConfigurationProperties("flows")
public record FlowProperties(
        @DefaultValue("2s") Duration pollInterval,
        @DefaultValue("15s") Duration scheduleInterval,
        @DefaultValue("1m") Duration lease,
        @DefaultValue("2") int heavySlots,
        @DefaultValue("8") int lightSlots,
        @DefaultValue("10m") Duration reportTimeout,
        @DefaultValue("1m") Duration lightTimeout,
        @DefaultValue("7d") Duration fileRetention,
        @DefaultValue("1h") Duration cleanupInterval,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("30s") Duration retryBackoff,
        @DefaultValue("10") int maxSchedulesPerFlow,
        @DefaultValue("5m") Duration minScheduleInterval,
        @DefaultValue("10s") Duration shutdownGrace,
        @DefaultValue Webhook webhook,
        @DefaultValue Kafka kafka) {

    /**
     * @param allowedHosts     hosts webhook steps may call: exact names, or {@code *.example.com} for subdomains.
     *                         Empty = none (a flow cannot reach into the company network by accident)
     * @param maxPayloadSize   largest body an incoming webhook may send
     * @param maxResponseChars a webhook step keeps at most this much of the answer in its output
     */
    public record Webhook(@DefaultValue List<String> allowedHosts,
                          @DefaultValue("256KB") DataSize maxPayloadSize,
                          @DefaultValue("4000") int maxResponseChars) {

        public boolean allows(String host) {
            if (host == null || allowedHosts == null) {
                return false;
            }
            String name = host.toLowerCase(java.util.Locale.ROOT);
            return allowedHosts.stream().map(pattern -> pattern.strip().toLowerCase(java.util.Locale.ROOT))
                    .anyMatch(pattern -> pattern.startsWith("*.") ? name.endsWith(pattern.substring(1))
                            : name.equals(pattern));
        }
    }

    /**
     * @param enabled           Kafka triggers and publish steps are available
     * @param allowedTopics     topics flows may publish to and listen on: exact names or prefixes ending in {@code *}
     * @param triggerGroupId    consumer group of the trigger listeners (all instances share it)
     * @param reconcileInterval how often each instance aligns its listeners with the triggers in the database
     */
    public record Kafka(@DefaultValue("false") boolean enabled,
                        @DefaultValue List<String> allowedTopics,
                        @DefaultValue("flow-triggers") String triggerGroupId,
                        @DefaultValue("30s") Duration reconcileInterval) {

        public boolean allows(String topic) {
            return topic != null && allowedTopics != null && allowedTopics.stream().map(String::strip)
                    .anyMatch(pattern -> pattern.endsWith("*") ? topic.startsWith(pattern.substring(0, pattern.length() - 1))
                            : topic.equals(pattern));
        }
    }
}
