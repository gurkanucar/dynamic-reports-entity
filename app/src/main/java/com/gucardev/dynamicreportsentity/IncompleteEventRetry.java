package com.gucardev.dynamicreportsentity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Events between modules are stored with the transaction that published them (Spring Modulith's event publication
 * registry) and handed to the listeners after the commit. A listener that failed (e.g. a decision that arrived
 * before its step started waiting, a database hiccup) leaves its publication incomplete; this delivers those again.
 * Every instance may do it; listeners ignore repeats.
 */
@Slf4j
@Component
@EnableAsync
@EnableScheduling
class IncompleteEventRetry {

    private final IncompleteEventPublications publications;

    IncompleteEventRetry(IncompleteEventPublications publications) {
        this.publications = publications;
    }

    @Scheduled(initialDelayString = "${events.retry-interval:PT30S}", fixedDelayString = "${events.retry-interval:PT30S}")
    void retry() {
        try {
            publications.resubmitIncompletePublicationsOlderThan(Duration.ofSeconds(10));
        } catch (RuntimeException e) {
            log.warn("Could not deliver incomplete events again: {}", e.getMessage());
        }
    }
}
