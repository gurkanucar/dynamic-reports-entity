package com.gucardev.dynamicreportsentity.platform.approval;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Every instance times out overdue requests; {@code SKIP LOCKED} gives each instance different ones. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApprovalProperties.class)
@EnableScheduling
class ApprovalConfiguration {

    private static final int BATCH = 100;

    private final ApprovalService approvals;

    ApprovalConfiguration(ApprovalService approvals) {
        this.approvals = approvals;
    }

    @Scheduled(initialDelayString = "${approvals.timeout-check-interval:PT30S}",
            fixedDelayString = "${approvals.timeout-check-interval:PT30S}")
    void timeOutOverdueRequests() {
        while (approvals.timeOutOverdue(BATCH) == BATCH) {
            // a full batch: there may be more
        }
    }
}
