package com.gucardev.dynamicreportsentity.reporting.query;

import com.gucardev.dynamicreportsentity.reporting.ReportViewer;
import com.gucardev.dynamicreportsentity.reporting.catalog.IndexAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Runs report queries in two modes:
 * <ul>
 *   <li>{@link #run} – one page for the screen ({@code LIMIT size+1} tells whether a next page exists
 *       without a costly {@code COUNT(*)}).</li>
 *   <li>{@link #writeCsv} – streams rows straight to the response; memory stays flat whatever the row count.</li>
 * </ul>
 */
@Slf4j
@Service
public class ReportExecutor {

    private final ReportQueryBuilder queryBuilder;
    private final JdbcTemplate jdbcTemplate;
    private final ReportingProperties properties;
    private final TransactionTemplate readOnlyTransaction;
    private final IndexAdvisor indexAdvisor;

    public ReportExecutor(ReportQueryBuilder queryBuilder, JdbcTemplate jdbcTemplate, ReportingProperties properties,
                          PlatformTransactionManager transactionManager, IndexAdvisor indexAdvisor) {
        this.queryBuilder = queryBuilder;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.indexAdvisor = indexAdvisor;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    /** Validates the definition and checks permissions; throws before anything is executed. */
    public ReportQuery prepare(ReportDefinition definition, ReportViewer viewer) {
        return queryBuilder.build(definition, viewer);
    }

    @Transactional(readOnly = true)
    public ReportResult run(ReportDefinition definition, ReportViewer viewer, int page, Integer requestedSize,
                            boolean includeTotal) {
        int size = Math.clamp(requestedSize == null ? properties.defaultPageSize() : requestedSize,
                1, properties.maxPageSize());
        int pageNumber = Math.max(page, 0);

        ReportQuery query = prepare(definition, viewer);
        String sql = query.sql() + "\nLIMIT ? OFFSET ?";
        List<Object> parameters = new ArrayList<>(query.parameters());
        parameters.add(size + 1);                     // one extra row => "is there a next page?"
        parameters.add((long) pageNumber * size);
        log.debug("Running report page {} (size {})\n{}\nparameters: {}", pageNumber, size, sql, parameters);

        int columnCount = query.columns().size();
        long start = System.nanoTime();
        List<List<Object>> rows = jdbcTemplate.query(statement(sql, parameters, 0, 0),
                (resultSet, rowNumber) -> readRow(resultSet, columnCount));
        recordTiming(query, start);

        boolean hasNext = rows.size() > size;
        List<List<Object>> pageRows = hasNext ? rows.subList(0, size) : rows;

        Long total = null;
        if (includeTotal) {
            if (!hasNext && (pageNumber == 0 || !pageRows.isEmpty())) {
                // Last page: everything before it was full, so the total is known without a second query.
                total = (long) pageNumber * size + pageRows.size();
            } else {
                log.debug("Counting report rows\n{}", query.countSql());
                total = jdbcTemplate.query(statement(query.countSql(), query.parameters(), 0, 0),
                        (resultSet, rowNumber) -> resultSet.getLong(1)).getFirst();
            }
        }
        return new ReportResult(query.columns(), pageRows, pageRows.size(), pageNumber, size, hasNext, total,
                indexAdvisor.hints(query.columnUsages()));
    }

    /**
     * Streams the query result as CSV. Runs inside a read-only transaction because some drivers
     * (notably PostgreSQL) only honour the fetch size with auto-commit off; without it they would
     * load the whole result into memory.
     */
    public void writeCsv(ReportQuery query, OutputStream outputStream) {
        int limit = properties.exportMaxRows();
        int columnCount = query.columns().size();
        log.debug("Exporting report\n{}\nparameters: {}", query.sql(), query.parameters());

        readOnlyTransaction.executeWithoutResult(status -> {
            try {
                Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, UTF_8));
                CsvWriter csv = new CsvWriter(writer);
                writer.write('﻿'); // BOM so Excel detects UTF-8
                csv.writeRow(query.columns().stream().map(ResultColumn::label).toList());

                int[] rowCount = {0};
                jdbcTemplate.query(statement(query.sql(), query.parameters(), properties.exportFetchSize(), limit + 1),
                        (RowCallbackHandler) resultSet -> {
                            if (++rowCount[0] > limit) {
                                return; // the extra row only tells us the export was cut off
                            }
                            try {
                                csv.writeRow(readRow(resultSet, columnCount));
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
                if (rowCount[0] > limit) {
                    csv.writeRow(List.of("Export truncated at " + limit
                            + " rows. Narrow the filters or request an offline export."));
                }
                writer.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * Feeds the index advisor (in-memory counters only) and warns about slow queries that use
     * unindexed columns. Costs a few hash lookups per query; no extra database call.
     */
    private void recordTiming(ReportQuery query, long startNanos) {
        long millis = (System.nanoTime() - startNanos) / 1_000_000;
        boolean slow = millis >= properties.slowQueryThreshold().toMillis();
        indexAdvisor.record(query.columnUsages(), millis, slow);
        if (slow) {
            List<String> hints = indexAdvisor.hints(query.columnUsages());
            log.warn("Slow report query ({} ms){}\n{}", millis,
                    hints.isEmpty() ? "" : "\nIndex hints:\n  " + String.join("\n  ", hints), query.sql());
        }
    }

    private PreparedStatementCreator statement(String sql, List<Object> parameters, int fetchSize, int maxRows) {
        return connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setQueryTimeout(Math.toIntExact(properties.queryTimeout().toSeconds()));
            if (fetchSize > 0) {
                statement.setFetchSize(fetchSize);
            }
            if (maxRows > 0) {
                statement.setMaxRows(maxRows);
            }
            new ArgumentPreparedStatementSetter(parameters.toArray()).setValues(statement);
            return statement;
        };
    }

    private static List<Object> readRow(ResultSet resultSet, int columnCount) throws SQLException {
        List<Object> row = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            row.add(normalize(resultSet.getObject(i)));
        }
        return row;
    }

    /** JDBC drivers may return java.sql types; expose java.time instead. */
    private static Object normalize(Object value) {
        return switch (value) {
            case Date date -> date.toLocalDate();
            case Timestamp timestamp -> timestamp.toLocalDateTime();
            case null, default -> value;
        };
    }
}
