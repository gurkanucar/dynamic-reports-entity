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
 * @param reportMaxRows       a report step fails above this many rows (a step may ask for less)
 * @param maxArtifactSize     largest file a report step may produce (attached to mails, so keep it modest)
 * @param fileRetention       files of runs are deleted this long after they were made (downloads then answer 410)
 * @param cleanupInterval     how often each instance deletes expired files
 * @param previewRows         rows of a report kept for the e-mail body ({@code steps.<key>.rows})
 * @param maxAttempts         default attempts per step (first try included)
 * @param retryBackoff        wait before the 2nd attempt; doubles for each further attempt (max 30 minutes)
 * @param maxSchedulesPerFlow  cron schedules per flow
 * @param minScheduleInterval  schedules may not fire more often than this
 * @param shutdownGrace        on shutdown, running steps are stopped and handed back after at most this long
 * @param mail                e-mail settings
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
        @DefaultValue("100000") int reportMaxRows,
        @DefaultValue("20MB") DataSize maxArtifactSize,
        @DefaultValue("7d") Duration fileRetention,
        @DefaultValue("1h") Duration cleanupInterval,
        @DefaultValue("20") int previewRows,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("30s") Duration retryBackoff,
        @DefaultValue("10") int maxSchedulesPerFlow,
        @DefaultValue("5m") Duration minScheduleInterval,
        @DefaultValue("10s") Duration shutdownGrace,
        @DefaultValue Mail mail) {

    /**
     * @param from                    sender address of flow e-mails
     * @param allowedRecipientDomains if not empty, flows may only mail these domains (e.g. the company's)
     */
    public record Mail(@DefaultValue("reports@localhost") String from,
                       @DefaultValue List<String> allowedRecipientDomains) {

        public boolean allows(String address) {
            if (allowedRecipientDomains == null || allowedRecipientDomains.isEmpty()) {
                return true;
            }
            String domain = address.substring(address.lastIndexOf('@') + 1).toLowerCase(java.util.Locale.ROOT);
            return allowedRecipientDomains.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(domain));
        }
    }
}
