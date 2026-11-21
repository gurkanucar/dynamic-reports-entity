package com.gucardev.dynamicreportsentity.platform.dataset.query;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Guard rails for dataset execution.
 *
 * @param defaultPageSize page size when the client sends none
 * @param maxPageSize     hard upper bound; larger requested sizes are clamped
 * @param queryTimeout    statement timeout so a runaway dataset cannot hog the database
 * @param exportMaxRows   cap for synchronous CSV exports; bigger exports belong in a background job
 * @param exportFetchSize JDBC fetch size while streaming exports (keeps memory flat)
 * @param slowQueryThreshold queries taking longer are logged with index hints and counted as slow
 */
@ConfigurationProperties("datasets")
public record DatasetProperties(
        @DefaultValue("200") int defaultPageSize,
        @DefaultValue("5000") int maxPageSize,
        @DefaultValue("30s") Duration queryTimeout,
        @DefaultValue("1000000") int exportMaxRows,
        @DefaultValue("1000") int exportFetchSize,
        @DefaultValue("500ms") Duration slowQueryThreshold) {
}
