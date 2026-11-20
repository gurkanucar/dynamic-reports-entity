package com.gucardev.dynamicreportsentity.platform.reporting.query;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReportingProperties.class)
class QueryConfiguration {

    /** Relative date filters (e.g. NEXT_N_DAYS) use this clock, so tests can pin "today". */
    @Bean
    Clock reportClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    SqlDialect sqlDialect(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return SqlDialect.fromProductName(connection.getMetaData().getDatabaseProductName());
        }
    }
}
