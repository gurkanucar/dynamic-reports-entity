package com.gucardev.dynamicreportsentity.platform.dataset.query;


/**
 * The few places where the generated SQL differs per database. Detected from the JDBC metadata
 * ({@link QueryConfiguration}); everything else is plain SQL that runs on both H2 and PostgreSQL.
 */
public enum SqlDialect {
    H2,
    POSTGRESQL,
    OTHER;

    static SqlDialect fromProductName(String productName) {
        String name = productName == null ? "" : productName.toLowerCase();
        return name.contains("h2") ? H2 : name.contains("postgres") ? POSTGRESQL : OTHER;
    }

    /**
     * Appended to a pre-aggregated derived table (sorted by its key column {@code k}).
     * H2 treats a plain derived table like a view and re-runs it for every outer row (43 s on 100k people);
     * {@code ORDER BY ... LIMIT} makes it compute the groups once (~1 s). PostgreSQL aggregates once anyway,
     * and the forced sort would cost it (3.7 s instead of 2.3 s on 1M people), so it gets nothing.
     */
    String materializeDerivedTable() {
        return this == H2 ? " ORDER BY k LIMIT " + Integer.MAX_VALUE : "";
    }

    /**
     * Whether per-root filters and sorts on child aggregates use one pre-aggregated join instead of a
     * correlated subquery per root row. PostgreSQL hash-joins the groups (#43 on 1M people: 2.8 s -> 2.0 s,
     * #42: the filter part 1.1 s -> 0.13 s). H2 cannot index a derived table, so joining 100k person
     * groups becomes a nested loop (timeout); its correlated subqueries use the foreign key index instead.
     */
    boolean preAggregatesPerRootFilters() {
        return this != H2;
    }

}
