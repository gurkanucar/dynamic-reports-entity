package com.gucardev.dynamicreportsentity.platform.dataset.snapshot;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Starts due snapshot refreshes. Checks once a minute ({@code datasets.snapshot-scheduler-interval}); the
 * refresh period itself is per snapshot ({@code refreshEveryHours}), counted from the end of its last refresh.
 * Runs on every app instance; {@link SnapshotService} lets only one of them refresh a given snapshot.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@RequiredArgsConstructor
class SnapshotScheduler {

    private final SnapshotService snapshots;

    @Scheduled(initialDelayString = "${datasets.snapshot-scheduler-interval:PT1M}",
            fixedDelayString = "${datasets.snapshot-scheduler-interval:PT1M}")
    void refreshDue() {
        snapshots.refreshDue();
    }
}
