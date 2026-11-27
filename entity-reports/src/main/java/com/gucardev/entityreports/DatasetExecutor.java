package com.gucardev.entityreports;

import com.gucardev.entityreports.Dataset.Aggregate;
import com.gucardev.entityreports.Dataset.CancelledException;
import com.gucardev.entityreports.Dataset.Definition;
import com.gucardev.entityreports.Dataset.ExportFormat;
import com.gucardev.entityreports.Dataset.InvalidException;
import com.gucardev.entityreports.Dataset.Query;
import com.gucardev.entityreports.Dataset.Result;
import com.gucardev.entityreports.Dataset.ResultColumn;
import com.gucardev.entityreports.Dataset.Viewer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.io.OutputStream;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Runs datasets:
 * <ul>
 *   <li>{@link #run} – one page for the screen ({@code LIMIT size+1} tells whether a next page exists without a
 *       costly {@code COUNT(*)});</li>
 *   <li>{@link #write} – streams an Excel/CSV file straight to a response; memory stays flat whatever the row
 *       count;</li>
 *   <li>{@link #export} – the same for background jobs: fails above a row limit, can be cancelled.</li>
 * </ul>
 */
public class DatasetExecutor {

    private static final Logger log = LoggerFactory.getLogger(DatasetExecutor.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final DatasetQueryBuilder queryBuilder;
    private final JdbcTemplate jdbcTemplate;
    private final EntityReportsProperties properties;
    private final TransactionTemplate readOnlyTransaction;
    private final IndexAdvisor indexAdvisor;

    public DatasetExecutor(DatasetQueryBuilder queryBuilder, JdbcTemplate jdbcTemplate, EntityReportsProperties properties,
                           PlatformTransactionManager transactionManager, IndexAdvisor indexAdvisor) {
        this.queryBuilder = queryBuilder;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.indexAdvisor = indexAdvisor;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    /** Receives the rows of {@link #export}, one at a time. */
    @FunctionalInterface
    public interface RowConsumer {
        void accept(List<Object> row);
    }

    /**
     * Lets another thread stop a running background query: {@link #cancel()} calls {@link Statement#cancel()} on the
     * statement being executed (the database stops working on it) and makes the row loop give up. A cancel that
     * arrives just before the statement is sent may be ignored by the driver; callers that keep calling
     * {@link #cancel()} (e.g. from a heartbeat) cover that window.
     */
    public static final class QueryHandle {

        private final AtomicReference<Statement> statement = new AtomicReference<>();
        private volatile boolean cancelled;

        public void cancel() {
            cancelled = true;
            Statement running = statement.get();
            if (running != null) {
                try {
                    running.cancel();
                } catch (SQLException ignored) {
                    // already finished or closed: nothing left to stop
                }
            }
        }

        public boolean isCancelled() {
            return cancelled;
        }

        void attach(Statement running) {
            statement.set(running);
            if (cancelled) {
                cancel();
            }
        }

        void detach() {
            statement.set(null);
        }
    }

    /** Validates the definition and checks permissions; throws before anything is executed. */
    public Query prepare(Definition definition, Viewer viewer) {
        return queryBuilder.build(definition, viewer);
    }

    /** One page; {@code includeTotal} adds a {@code COUNT(*)} only when the page does not tell the total itself. */
    public Result run(Definition definition, Viewer viewer, int page, Integer requestedSize, boolean includeTotal) {
        int size = Math.clamp(requestedSize == null ? properties.defaultPageSize() : requestedSize,
                1, properties.maxPageSize());
        int pageNumber = Math.max(page, 0);
        Query query = prepare(definition, viewer);
        return readOnlyTransaction.execute(status -> runPage(query, pageNumber, size, includeTotal));
    }

    private Result runPage(Query query, int pageNumber, int size, boolean includeTotal) {
        String sql = query.sql() + "\nLIMIT ? OFFSET ?";
        List<Object> parameters = new ArrayList<>(query.parameters());
        parameters.add(size + 1);                     // one extra row => "is there a next page?"
        parameters.add((long) pageNumber * size);
        log.debug("Running dataset page {} (size {})\n{}\nparameters: {}", pageNumber, size, sql, parameters);

        long start = System.nanoTime();
        Set<Integer> truncated = new TreeSet<>();
        List<List<Object>> rows = jdbcTemplate.query(statement(sql, parameters, 0, 0, properties.queryTimeout()),
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
                log.debug("Counting dataset rows\n{}", query.countSql());
                total = jdbcTemplate.query(statement(query.countSql(), query.countParameters(), 0, 0,
                        properties.queryTimeout()), (resultSet, rowNumber) -> resultSet.getLong(1)).getFirst();
            }
        }
        List<String> truncatedLists = truncated.stream().map(index -> query.columns().get(index).label()).toList();
        return new Result(query.columns(), pageRows, pageRows.size(), pageNumber, size, hasNext, total,
                truncatedLists, indexAdvisor.hints(query.columnUsages()));
    }

    /**
     * Streams the result as a file (Excel or CSV). Runs inside a read-only transaction because some drivers (notably
     * PostgreSQL) only honour the fetch size with auto-commit off; without it they would load the whole result into
     * memory. Above {@code entity-reports.export-max-rows} the file ends with a note instead of failing.
     */
    public void write(Query query, ExportFormat format, OutputStream outputStream) {
        int limit = properties.exportMaxRows();
        log.debug("Exporting dataset as {}\n{}\nparameters: {}", format, query.sql(), query.parameters());
        readOnlyTransaction.executeWithoutResult(status -> {
            TableWriter table = TableWriter.of(format, outputStream);
            table.header(query.columns());
            int[] rowCount = {0};
            jdbcTemplate.query(statement(query.sql(), query.parameters(), properties.exportFetchSize(), limit + 1,
                    properties.queryTimeout()), (RowCallbackHandler) resultSet -> {
                if (++rowCount[0] > limit) {
                    return; // the extra row only tells us the export was cut off
                }
                table.row(readRow(resultSet, query.columns(), new TreeSet<>()));
            });
            if (rowCount[0] > limit) {
                table.note("Export truncated at " + limit + " rows. Narrow the filters or request an offline export.");
            }
            table.finish();
        });
    }

    /**
     * Background export (scheduled jobs): streams the rows as a file to {@code outputStream} and hands each row to
     * {@code rowListener} as well (e.g. to keep the first rows for an e-mail). Unlike {@link #write}, it fails with
     * {@link InvalidException} above {@code maxRows} instead of cutting the file, and it can be stopped from another
     * thread through {@code handle} ({@link CancelledException}).
     *
     * @return the number of data rows written
     */
    public long export(Query query, ExportFormat format, OutputStream outputStream, int maxRows, Duration timeout,
                       QueryHandle handle, RowConsumer rowListener) {
        log.debug("Exporting dataset as {} in the background\n{}\nparameters: {}", format, query.sql(),
                query.parameters());
        long start = System.nanoTime();
        long[] rowCount = {0};
        PreparedStatementCreator statement = statement(query.sql(), query.parameters(), properties.exportFetchSize(),
                maxRows + 1, timeout);
        try {
            readOnlyTransaction.executeWithoutResult(status -> {
                TableWriter table = TableWriter.of(format, outputStream);
                table.header(query.columns());
                jdbcTemplate.query(connection -> {
                    PreparedStatement prepared = statement.createPreparedStatement(connection);
                    handle.attach(prepared);
                    return prepared;
                }, (RowCallbackHandler) resultSet -> {
                    if (handle.isCancelled()) {
                        throw new CancelledException();
                    }
                    if (++rowCount[0] > maxRows) {
                        throw new InvalidException("The dataset has more than " + maxRows + " rows; narrow the filters");
                    }
                    List<Object> row = readRow(resultSet, query.columns(), new TreeSet<>());
                    rowListener.accept(row);
                    table.row(row);
                });
                table.finish();
            });
        } catch (RuntimeException e) {
            if (handle.isCancelled()) {
                throw new CancelledException();   // the driver reports a cancelled statement as an SQL error
            }
            throw e;
        } finally {
            handle.detach();
        }
        recordTiming(query, start);
        return rowCount[0];
    }

    /**
     * Feeds the index advisor (in-memory counters only) and warns about slow queries that use unindexed columns.
     * Costs a few hash lookups per query; no extra database call.
     */
    private void recordTiming(Query query, long startNanos) {
        long millis = (System.nanoTime() - startNanos) / 1_000_000;
        boolean slow = millis >= properties.slowQueryThreshold().toMillis();
        indexAdvisor.record(query.columnUsages(), millis, slow);
        if (slow) {
            List<String> hints = indexAdvisor.hints(query.columnUsages());
            log.warn("Slow dataset query ({} ms){}\n{}", millis,
                    hints.isEmpty() ? "" : "\nIndex hints:\n  " + String.join("\n  ", hints), query.sql());
        }
    }

    private static PreparedStatementCreator statement(String sql, List<Object> parameters, int fetchSize, int maxRows,
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
     * Reads one row. LIST cells become JSON arrays (objects for a LIST of objects); a list that came back longer than
     * its limit is cut to the limit and its column index is added to {@code truncated}.
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
