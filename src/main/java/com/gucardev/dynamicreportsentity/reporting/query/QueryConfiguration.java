package com.gucardev.dynamicreportsentity.reporting.query;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReportingProperties.class)
class QueryConfiguration {

    /** Relative date filters (e.g. NEXT_N_DAYS) use this clock, so tests can pin "today". */
    @Bean
    Clock reportClock() {
        return Clock.systemDefaultZone();
    }
}
