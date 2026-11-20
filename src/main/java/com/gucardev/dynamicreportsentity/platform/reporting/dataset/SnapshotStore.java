package com.gucardev.dynamicreportsentity.platform.reporting.dataset;

import com.gucardev.dynamicreportsentity.platform.reporting.catalog.FieldType;
import com.gucardev.dynamicreportsentity.platform.reporting.query.Aggregate;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ReportExecutor;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ReportQuery;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ResultColumn;
import com.gucardev.dynamicreportsentity.platform.reporting.query.SqlDialect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The physical snapshot tables: {@code rds_<dataset>_<version> (row_no, c0, c1, ...)}.
 * <p>
 * Filled by streaming the report through the engine ({@link ReportExecutor#forEachRow}) and inserting in
 * batches with bind parameters. A PostgreSQL {@code MATERIALIZED VIEW} would freeze the SQL text: bind
 * parameters (user values) would have to be inlined into DDL, and relative dates ({@code LAST_N_DAYS})
 * would keep the day the view was created. Rebuilding through the engine renders the query fresh on
 * every refresh, works on H2 and PostgreSQL alike, and stores LIST cells as JSON.
 */
@Component
class SnapshotStore {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern TABLE_NAME = Pattern.compile("rds_[a-z][a-z0-9_]*_[0-9]+");
    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final SqlDialect dialect;
    private final ReportExecutor executor;
    /** Inserts commit in their own transaction, beside the read-only one that streams the report. */
    private final TransactionTemplate writeTransaction;

    SnapshotStore(JdbcTemplate jdbcTemplate, SqlDialect dialect, ReportExecutor executor,
                  PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialect = dialect;
        this.executor = executor;
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    static String tableName(String dataset, long version) {
        return checked("rds_" + dataset + "_" + version);
    }

    /** Creates {@code table} and fills it with every row of {@code query}; returns the row count. */
    long build(String table, ReportQuery query, int maxRows, Duration timeout) {
        List<ResultColumn> columns = query.columns();
        jdbcTemplate.execute("CREATE TABLE " + checked(table) + " (row_no BIGINT PRIMARY KEY"
                + IntStream.range(0, columns.size())
                .mapToObj(i -> ", c" + i + " " + dialect.columnType(columns.get(i).type(), isList(columns.get(i))))
                .collect(Collectors.joining()) + ")");

        String insert = "INSERT INTO " + table + " (row_no" + IntStream.range(0, columns.size()).mapToObj(i -> ", c" + i)
                .collect(Collectors.joining()) + ") VALUES (?" + ", ?".repeat(columns.size()) + ")";
        int[] types = new int[columns.size() + 1];
        types[0] = Types.BIGINT;
        for (int i = 0; i < columns.size(); i++) {
            types[i + 1] = sqlType(columns.get(i));
        }
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        long[] rowNumber = {0};
        executor.forEachRow(query, maxRows, timeout, row -> {
            Object[] values = new Object[columns.size() + 1];
            values[0] = ++rowNumber[0];
            for (int i = 0; i < columns.size(); i++) {
                Object value = row.get(i);
                values[i + 1] = isList(columns.get(i)) ? JSON.writeValueAsString(value) : value;
            }
            batch.add(values);
            if (batch.size() == BATCH_SIZE) {
                flush(insert, batch, types);
            }
        });
        flush(insert, batch, types);
        return rowNumber[0];
    }

    /** One page of a snapshot, in report order; LIST cells are parsed back from JSON. */
    List<List<Object>> page(String table, List<ResultColumn> columns, int limit, long offset) {
        String sql = "SELECT " + IntStream.range(0, columns.size()).mapToObj(i -> "c" + i).collect(Collectors.joining(", "))
                + " FROM " + checked(table) + " ORDER BY row_no LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> readRow(resultSet, columns), limit, offset);
    }

    void drop(String table) {
        if (table != null) {
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + checked(table));
        }
    }

    /**
     * Drops the snapshot tables of {@code dataset} with a version below {@code version}: leftovers of
     * refreshes that died with their instance, or of a deleted dataset. Higher versions may belong to a
     * refresh another instance is running right now, so they are left alone.
     */
    void dropOlderThan(String dataset, long version) {
        Pattern own = Pattern.compile("rds_" + Pattern.quote(dataset) + "_([0-9]+)");
        List<String> tables = jdbcTemplate.execute((java.sql.Connection connection) -> {
            List<String> found = new ArrayList<>();
            try (ResultSet resultSet = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
                while (resultSet.next()) {
                    String name = resultSet.getString("TABLE_NAME").toLowerCase(java.util.Locale.ROOT);
                    java.util.regex.Matcher matcher = own.matcher(name);
                    if (matcher.matches() && Long.parseLong(matcher.group(1)) < version) {
                        found.add(name);
                    }
                }
            }
            return found;
        });
        if (tables != null) {
            tables.forEach(this::drop);
        }
    }

    private void flush(String insert, List<Object[]> batch, int[] types) {
        if (!batch.isEmpty()) {
            writeTransaction.executeWithoutResult(status -> jdbcTemplate.batchUpdate(insert, batch, types));
            batch.clear();
        }
    }

    private static List<Object> readRow(ResultSet resultSet, List<ResultColumn> columns) throws SQLException {
        List<Object> row = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            Object value = resultSet.getObject(i + 1);
            boolean list = isList(columns.get(i));
            row.add(switch (value) {
                case null -> list ? List.of() : null;
                case String json when list -> JSON.readValue(json, List.class);
                case Date date -> date.toLocalDate();
                case Timestamp timestamp -> timestamp.toLocalDateTime();
                // H2's DECFLOAT may hand back 5E+1 for 50
                case BigDecimal number when number.scale() < 0 -> number.setScale(0);
                default -> value;
            });
        }
        return row;
    }

    private static boolean isList(ResultColumn column) {
        return column.aggregate() == Aggregate.LIST;
    }

    private static int sqlType(ResultColumn column) {
        if (isList(column)) {
            return Types.VARCHAR;
        }
        return switch (column.type() == null ? FieldType.TEXT : column.type()) {
            case TEXT -> Types.VARCHAR;
            case NUMBER -> Types.NUMERIC;
            case DATE -> Types.DATE;
            case DATE_TIME -> Types.TIMESTAMP;
            case BOOLEAN -> Types.BOOLEAN;
        };
    }

    /** Table names end up in DDL; they are built from validated dataset names, checked once more here. */
    private static String checked(String table) {
        if (!TABLE_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException("Not a snapshot table name: " + table);
        }
        return table;
    }
}
