package com.gucardev.dynamicreportsentity.platform.reporting.query;

import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import com.gucardev.dynamicreportsentity.platform.reporting.catalog.IndexAdvisor;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import tools.jackson.databind.json.JsonMapper;

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
    public ReportQuery prepare(ReportDefinition definition, Actor viewer) {
        return queryBuilder.build(definition, viewer);
    }

    @Transactional(readOnly = true)
    public ReportResult run(ReportDefinition definition, Actor viewer, int page, Integer requestedSize,
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

        long start = System.nanoTime();
        Set<Integer> truncated = new TreeSet<>();
        List<List<Object>> rows = jdbcTemplate.query(statement(sql, parameters, 0, 0),
                (resultSet, rowNumber) -> readRow(resultSet, query.columns(), truncated));
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
                total = jdbcTemplate.query(statement(query.countSql(), query.countParameters(), 0, 0),
                        (resultSet, rowNumber) -> resultSet.getLong(1)).getFirst();
            }
        }
        List<String> truncatedLists = truncated.stream().map(index -> query.columns().get(index).label()).toList();
        return new ReportResult(query.columns(), pageRows, pageRows.size(), pageNumber, size, hasNext, total,
                truncatedLists, indexAdvisor.hints(query.columnUsages()));
    }

    /**
     * Streams the query result as CSV. Runs inside a read-only transaction because some drivers
     * (notably PostgreSQL) only honour the fetch size with auto-commit off; without it they would
     * load the whole result into memory.
     */
    public void writeCsv(ReportQuery query, OutputStream outputStream) {
        int limit = properties.exportMaxRows();
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
                                csv.writeRow(readRow(resultSet, query.columns(), new TreeSet<>()));
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

    /** Receives the rows of {@link #forEachRow}, one at a time. */
    @FunctionalInterface
    public interface RowConsumer {
        void accept(List<Object> row);
    }

    /**
     * Streams every row of the query (LIST cells as lists, like {@link #run}) to {@code consumer}; used to
     * fill dataset snapshots. Fails with {@link InvalidReportException} when there are more than
     * {@code maxRows} rows, so a snapshot is never silently cut.
     *
     * @return the number of rows
     */
    public long forEachRow(ReportQuery query, int maxRows, Duration timeout, RowConsumer consumer) {
        log.debug("Streaming report\n{}\nparameters: {}", query.sql(), query.parameters());
        long start = System.nanoTime();
        long[] rowCount = {0};
        readOnlyTransaction.executeWithoutResult(status -> jdbcTemplate.query(
                statement(query.sql(), query.parameters(), properties.exportFetchSize(), maxRows + 1, timeout),
                (RowCallbackHandler) resultSet -> {
                    if (++rowCount[0] > maxRows) {
                        throw new InvalidReportException("The report has more than " + maxRows
                                + " rows; narrow the filters or raise reporting.dataset-max-rows");
                    }
                    consumer.accept(readRow(resultSet, query.columns(), new TreeSet<>()));
                }));
        recordTiming(query, start);
        return rowCount[0];
    }

    /**
     * Background CSV export (scheduled flows): streams the rows as CSV to {@code outputStream} and hands each
     * row to {@code rowListener} as well (e.g. to keep the first rows for an e-mail). Unlike {@link #writeCsv},
     * it fails with {@link InvalidReportException} above {@code maxRows} instead of cutting the file, and it can
     * be stopped from another thread through {@code handle} ({@link QueryCancelledException}).
     *
     * @return the number of data rows written
     */
    public long exportCsv(ReportQuery query, OutputStream outputStream, int maxRows, Duration timeout,
                          QueryHandle handle, RowConsumer rowListener) {
        log.debug("Exporting report in the background\n{}\nparameters: {}", query.sql(), query.parameters());
        long start = System.nanoTime();
        long[] rowCount = {0};
        PreparedStatementCreator statement = statement(query.sql(), query.parameters(), properties.exportFetchSize(),
                maxRows + 1, timeout);
        try {
            readOnlyTransaction.executeWithoutResult(status -> {
                try {
                    Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, UTF_8));
                    CsvWriter csv = new CsvWriter(writer);
                    writer.write('﻿'); // BOM so Excel detects UTF-8
                    csv.writeRow(query.columns().stream().map(ResultColumn::label).toList());
                    jdbcTemplate.query(connection -> {
                        PreparedStatement prepared = statement.createPreparedStatement(connection);
                        handle.attach(prepared);
                        return prepared;
                    }, (RowCallbackHandler) resultSet -> {
                        if (handle.isCancelled()) {
                            throw new QueryCancelledException();
                        }
                        if (++rowCount[0] > maxRows) {
                            throw new InvalidReportException("The report has more than " + maxRows
                                    + " rows; narrow the filters");
                        }
                        List<Object> row = readRow(resultSet, query.columns(), new TreeSet<>());
                        rowListener.accept(row);
                        try {
                            csv.writeRow(row);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                    writer.flush();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (RuntimeException e) {
            if (handle.isCancelled()) {
                throw new QueryCancelledException();   // the driver reports a cancelled statement as an SQL error
            }
            throw e;
        } finally {
            handle.detach();
        }
        recordTiming(query, start);
        return rowCount[0];
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
        return statement(sql, parameters, fetchSize, maxRows, properties.queryTimeout());
    }

    private PreparedStatementCreator statement(String sql, List<Object> parameters, int fetchSize, int maxRows,
                                               Duration timeout) {
        return connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setQueryTimeout(Math.toIntExact(timeout.toSeconds()));
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

    /**
     * Reads one row. LIST cells become JSON arrays (objects for a LIST of objects); a list that came back with
     * one element more than its limit is cut to the limit and its column index is added to {@code truncated}.
     */
    private static List<Object> readRow(ResultSet resultSet, List<ResultColumn> columns, Set<Integer> truncated)
            throws SQLException {
        List<Object> row = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            ResultColumn column = columns.get(i);
            Object value = normalize(resultSet.getObject(i + 1));
            if (column.aggregate() == Aggregate.LIST) {
                List<?> list = switch (value) {
                    case null -> List.of();   // no matching children
                    case List<?> elements -> elements;
                    case byte[] json -> JSON.readValue(new String(json, UTF_8), List.class);
                    default -> JSON.readValue(value.toString(), List.class);
                };
                if (column.listLimit() != null && list.size() > column.listLimit()) {
                    list = list.subList(0, column.listLimit());
                    truncated.add(i);
                }
                value = list;
            }
            row.add(value);
        }
        return row;
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** JDBC drivers may return java.sql types; expose java.time (and SQL arrays as lists) instead. */
    private static Object normalize(Object value) throws SQLException {
        return switch (value) {
            case Date date -> date.toLocalDate();
            case Timestamp timestamp -> timestamp.toLocalDateTime();
            case java.sql.Array array -> {
                Object[] elements = (Object[]) array.getArray();
                List<Object> list = new ArrayList<>(elements.length);
                for (Object element : elements) {
                    list.add(normalize(element));
                }
                array.free();
                yield list;
            }
            case null, default -> value;
        };
    }
}
