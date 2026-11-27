package com.gucardev.entityreports;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code entity-reports.*}: guard rails and switches. Every value has a sensible default.
 *
 * @param defaultPageSize    page size when the client sends none
 * @param maxPageSize        hard upper bound; larger requested sizes are clamped
 * @param queryTimeout       statement timeout so a runaway dataset cannot hog the database
 * @param exportMaxRows      cap for downloads; bigger exports belong in a background job
 * @param exportFetchSize    JDBC fetch size while streaming exports (keeps memory flat)
 * @param slowQueryThreshold queries taking longer are logged with index hints and counted as slow
 * @param adminAuthority     may see the generated SQL and change anyone's saved datasets; empty = nobody
 * @param indexCheckOnStartup warn at startup about relation columns without an index
 * @param web                the REST endpoints
 * @param saved              saved datasets
 */
@ConfigurationProperties("entity-reports")
public record EntityReportsProperties(
        @DefaultValue("200") int defaultPageSize,
        @DefaultValue("5000") int maxPageSize,
        @DefaultValue("30s") Duration queryTimeout,
        @DefaultValue("1000000") int exportMaxRows,
        @DefaultValue("1000") int exportFetchSize,
        @DefaultValue("500ms") Duration slowQueryThreshold,
        @DefaultValue("") String adminAuthority,
        @DefaultValue("true") boolean indexCheckOnStartup,
        @DefaultValue Web web,
        @DefaultValue Saved saved) {

    /**
     * @param enabled       register the controllers
     * @param path          dataset endpoints: catalog, run, export, preview, index-advice
     * @param savedPath     saved dataset CRUD
     * @param userAuthority needed for every endpoint; empty = any caller that reached the endpoint (secure the
     *                      paths in your own security configuration then)
     */
    public record Web(@DefaultValue("true") boolean enabled,
                      @DefaultValue("/api/datasets") String path,
                      @DefaultValue("/api/saved-datasets") String savedPath,
                      @DefaultValue("") String userAuthority) {
    }

    /**
     * @param enabled          register {@link SavedDatasets} (needs table {@code saved_dataset})
     * @param initializeSchema create the table at startup if it does not exist (simple setups; with Flyway/Liquibase
     *                         copy {@code META-INF/entity-reports/schema.sql} into a migration instead)
     */
    public record Saved(@DefaultValue("true") boolean enabled,
                        @DefaultValue("false") boolean initializeSchema) {
    }
}
