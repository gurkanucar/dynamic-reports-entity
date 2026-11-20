package com.gucardev.dynamicreportsentity.reporting.catalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Index advice for report queries, cheap enough to run on every query:
 * <ul>
 *   <li>the database's indexes are read <b>once</b> (at startup, and again when an admin asks for
 *       advice, so indexes added later by a DBA are picked up);</li>
 *   <li>each query only compares the columns it uses (collected while its SQL was built) with that
 *       cached set: a few hash lookups, no extra database round trip, no EXPLAIN;</li>
 *   <li>per unindexed column it keeps counters (uses, slow uses, total/max time). The map is bounded
 *       by the number of catalog columns, so memory stays constant.</li>
 * </ul>
 * It only knows structure, not data distribution: an index on a column where 95% of rows share
 * one value rarely helps. Hence the timings, so the team decides with real numbers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexAdvisor implements ApplicationRunner {

    private final ReportCatalog catalog;
    private final DataSource dataSource;

    /** table → leading column of every index on it (lower case). */
    private volatile Map<String, Set<String>> indexedColumns = Map.of();
    private final Map<ColumnUsageKey, Stats> stats = new ConcurrentHashMap<>();

    /** A join column without an index on its table. */
    public record MissingIndex(String table, String column, String usedBy) {
    }

    /** Aggregated advice for one column, for admins. */
    public record Advice(String table, String column, Set<ColumnUsage.Kind> usedFor, long uses, long slowUses,
                         long averageMillis, long maxMillis, List<String> suggestions) {
    }

    private record ColumnUsageKey(String table, String column) {
    }

    private static final class Stats {
        final Set<ColumnUsage.Kind> kinds = ConcurrentHashMap.newKeySet();
        final AtomicLong uses = new AtomicLong();
        final AtomicLong slowUses = new AtomicLong();
        final AtomicLong totalMillis = new AtomicLong();
        final AtomicLong maxMillis = new AtomicLong();
    }

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        refreshIndexes();
        List<MissingIndex> missing = findMissingIndexes();
        missing.forEach(gap -> log.warn("No index on {}.{} (joined by {}); reports through this relation will scan "
                + "the whole table. Add one, e.g. @Index(columnList = \"{}\").", gap.table(), gap.column(), gap.usedBy(), gap.column()));
        if (missing.isEmpty()) {
            log.info("Index check: every report join column is indexed");
        }
    }

    /** Re-reads index metadata of all catalog tables (one metadata call per table). */
    public void refreshIndexes() throws SQLException {
        Map<String, Set<String>> indexes = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            for (ReportableEntity entity : catalog.entities()) {
                indexes.put(entity.table(), leadingIndexColumns(metaData, entity.table()));
            }
        }
        indexedColumns = Map.copyOf(indexes);
    }

    public List<MissingIndex> findMissingIndexes() throws SQLException {
        refreshIndexes();
        Set<String> seen = new LinkedHashSet<>();
        List<MissingIndex> missing = new ArrayList<>();
        for (ReportableEntity entity : catalog.entities()) {
            for (Navigation navigation : entity.navigations().values()) {
                ReportableEntity joined = catalog.entity(navigation.targetEntity()).orElseThrow();
                if (seen.add(joined.table() + "." + navigation.joinColumn()) && !isIndexed(joined.table(), navigation.joinColumn())) {
                    missing.add(new MissingIndex(joined.table(), navigation.joinColumn(), entity.name() + "." + navigation.name()));
                }
            }
        }
        return missing;
    }

    /** Usages a regular index on the column would not serve (cheap: hash lookups only). */
    public List<ColumnUsage> unindexed(List<ColumnUsage> usages) {
        return usages.stream().filter(usage -> needsAttention(usage)).toList();
    }

    /** Called after every report run; only unindexed usages are tracked. */
    public void record(List<ColumnUsage> usages, long millis, boolean slow) {
        for (ColumnUsage usage : unindexed(usages)) {
            Stats columnStats = stats.computeIfAbsent(new ColumnUsageKey(usage.table(), usage.column()), key -> new Stats());
            columnStats.kinds.add(usage.kind());
            columnStats.uses.incrementAndGet();
            columnStats.totalMillis.addAndGet(millis);
            columnStats.maxMillis.accumulateAndGet(millis, Math::max);
            if (slow) {
                columnStats.slowUses.incrementAndGet();
            }
        }
    }

    /** Current advice, most expensive first. Columns indexed in the meantime drop out. */
    public List<Advice> advice() throws SQLException {
        refreshIndexes();
        List<Advice> result = new ArrayList<>();
        stats.forEach((key, columnStats) -> {
            Set<ColumnUsage.Kind> kinds = EnumSet.noneOf(ColumnUsage.Kind.class);
            columnStats.kinds.stream()
                    .filter(kind -> needsAttention(new ColumnUsage(key.table(), key.column(), kind)))
                    .forEach(kinds::add);
            if (kinds.isEmpty()) {
                return;
            }
            long uses = columnStats.uses.get();
            result.add(new Advice(key.table(), key.column(), kinds, uses, columnStats.slowUses.get(),
                    uses == 0 ? 0 : columnStats.totalMillis.get() / uses, columnStats.maxMillis.get(),
                    suggestions(key.table(), key.column(), kinds)));
        });
        result.sort(Comparator.comparingLong((Advice advice) -> advice.averageMillis() * advice.uses()).reversed());
        return result;
    }

    /** Human-readable hints for one query (used by the SQL preview). */
    public List<String> hints(List<ColumnUsage> usages) {
        Map<String, Set<ColumnUsage.Kind>> byColumn = new java.util.LinkedHashMap<>();
        unindexed(usages).forEach(usage -> byColumn
                .computeIfAbsent(usage.table() + "." + usage.column(), key -> EnumSet.noneOf(ColumnUsage.Kind.class))
                .add(usage.kind()));
        List<String> hints = new ArrayList<>();
        byColumn.forEach((qualified, kinds) -> {
            String[] parts = qualified.split("\\.", 2);
            suggestions(parts[0], parts[1], kinds).forEach(suggestion -> hints.add(qualified + " " + kinds + ": " + suggestion));
        });
        return hints;
    }

    private boolean needsAttention(ColumnUsage usage) {
        return switch (usage.kind()) {
            // A plain index does not serve these, indexed or not.
            case FILTER_EXPRESSION, FILTER_CONTAINS -> true;
            default -> !isIndexed(usage.table(), usage.column());
        };
    }

    private boolean isIndexed(String table, String column) {
        return indexedColumns.getOrDefault(table, Set.of()).contains(column);
    }

    private List<String> suggestions(String table, String column, Set<ColumnUsage.Kind> kinds) {
        List<String> suggestions = new ArrayList<>();
        if (!isIndexed(table, column) && kinds.stream().anyMatch(kind ->
                kind == ColumnUsage.Kind.JOIN || kind == ColumnUsage.Kind.FILTER || kind == ColumnUsage.Kind.SORT)) {
            suggestions.add("CREATE INDEX ix_" + table + "_" + column + " ON " + table + " (" + column + ");");
        }
        if (kinds.contains(ColumnUsage.Kind.FILTER_EXPRESSION)) {
            suggestions.add("Filtered through a date function (YEAR, MONTH...), so a plain index is not used. "
                    + "Prefer a date range (BETWEEN / NEXT_N_DAYS), or add an expression index, e.g. "
                    + "CREATE INDEX ix_" + table + "_" + column + "_year ON " + table + " ((EXTRACT(YEAR FROM " + column + ")));");
        }
        if (kinds.contains(ColumnUsage.Kind.FILTER_CONTAINS)) {
            suggestions.add("CONTAINS (LIKE '%...%') cannot use a B-tree index. Prefer STARTS_WITH, or on PostgreSQL: "
                    + "CREATE EXTENSION IF NOT EXISTS pg_trgm; CREATE INDEX ix_" + table + "_" + column + "_trgm ON "
                    + table + " USING gin (" + column + " gin_trgm_ops);");
        }
        return suggestions;
    }

    /** First column of every index (primary key included) on the table, lower-cased. */
    private static Set<String> leadingIndexColumns(DatabaseMetaData metaData, String table) throws SQLException {
        String name = metaData.storesUpperCaseIdentifiers() ? table.toUpperCase(Locale.ROOT) : table;
        Set<String> columns = new LinkedHashSet<>();
        try (ResultSet indexes = metaData.getIndexInfo(null, null, name, false, true)) {
            while (indexes.next()) {
                if (indexes.getShort("ORDINAL_POSITION") == 1 && indexes.getString("COLUMN_NAME") != null) {
                    columns.add(indexes.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        try (ResultSet primaryKey = metaData.getPrimaryKeys(null, null, name)) {
            while (primaryKey.next()) {
                if (primaryKey.getShort("KEY_SEQ") == 1) {
                    columns.add(primaryKey.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        return columns;
    }
}
