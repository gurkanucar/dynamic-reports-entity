package com.gucardev.dynamicreportsentity.reporting.query;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Guard rails for report execution.
 *
 * @param defaultPageSize page size when the client sends none
 * @param maxPageSize     hard upper bound; larger requested sizes are clamped
 * @param queryTimeout    statement timeout so a runaway report cannot hog the database
 * @param exportMaxRows   cap for synchronous CSV exports; bigger exports belong in a background job
 * @param exportFetchSize JDBC fetch size while streaming exports (keeps memory flat)
 * @param slowQueryThreshold queries taking longer are logged with index hints and counted as slow
 * @param datasetMaxRows       a snapshot refresh fails (keeping the previous snapshot) above this many rows
 * @param datasetRefreshTimeout statement timeout of a snapshot refresh (runs in the background, may be long)
 * @param datasetRefreshLease   how long a refresh claim holds without renewal; when an instance dies
 *                              mid-refresh, another one takes over after this (renewed every quarter of it)
 */
@ConfigurationProperties("reporting")
public record ReportingProperties(
        @DefaultValue("200") int defaultPageSize,
        @DefaultValue("5000") int maxPageSize,
        @DefaultValue("30s") Duration queryTimeout,
        @DefaultValue("1000000") int exportMaxRows,
        @DefaultValue("1000") int exportFetchSize,
        @DefaultValue("500ms") Duration slowQueryThreshold,
        @DefaultValue("5000000") int datasetMaxRows,
        @DefaultValue("10m") Duration datasetRefreshTimeout,
        @DefaultValue("2m") Duration datasetRefreshLease) {
}
