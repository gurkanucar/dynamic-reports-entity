package com.gucardev.dynamicreportsentity.reporting.dataset;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Starts due snapshot refreshes. Checks once a minute ({@code reporting.dataset-scheduler-interval}); the
 * refresh period itself is per dataset ({@code refreshEveryHours}), counted from the end of its last refresh.
 * Runs on every app instance; {@link DatasetService} lets only one of them refresh a given dataset.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@RequiredArgsConstructor
class DatasetScheduler {

    private final DatasetService datasets;

    @Scheduled(initialDelayString = "${reporting.dataset-scheduler-interval:PT1M}",
            fixedDelayString = "${reporting.dataset-scheduler-interval:PT1M}")
    void refreshDue() {
        datasets.refreshDue();
    }
}
