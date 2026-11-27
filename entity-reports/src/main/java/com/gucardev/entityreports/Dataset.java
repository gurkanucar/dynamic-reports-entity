package com.gucardev.entityreports;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Everything a caller of entity-reports exchanges with it, in one place: the definition an analyst builds
 * ({@link Definition}), what comes back ({@link Result}), who asks ({@link Viewer}), the row-level security hook
 * ({@link RowPolicy}) and the errors. Only data and tiny helpers live here; the engine is
 * {@link DatasetQueryBuilder} (SQL) and {@link DatasetExecutor} (running, paging, exports).
 */
public final class Dataset {

    private Dataset() {
    }

    // ==== definition: what the analyst builds =========================================================

    /**
     * Fields are paths starting at {@code root}. Example – departments with at least 3 active people, by average
     * salary:
     * <pre>{@code
     * {
     *   "root": "Personnel",
     *   "columns": [ {"field": "department.name"},
     *                {"aggregate": "COUNT", "label": "Headcount"},
     *                {"field": "salary", "aggregate": "AVG"} ],
     *   "where":   {"field": "active", "operator": "EQ", "value": true},
     *   "having":  {"aggregate": "COUNT", "operator": "GTE", "value": 3},
     *   "sort":    [ {"field": "salary", "aggregate": "AVG", "direction": "DESC"} ]
     * }
     * }</pre>
     *
     * @param perRoot one row per root entity, with aggregates over one-to-many relations computed per row
     *                (null = automatic: on when the only aggregates are LISTs; false = always group by the columns)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Definition(@NotBlank String root,
                             @NotEmpty List<@NotNull @Valid Column> columns,
                             @Valid Condition where,
                             @Valid Condition having,
                             List<@NotNull @Valid Sort> sort,
                             Boolean perRoot) {

        public Definition {
            sort = sort == null ? List.of() : sort;
        }

        public Definition(String root, List<Column> columns, Condition where, Condition having, List<Sort> sort) {
            this(root, columns, where, having, sort, null);
        }
    }

    /**
     * A selected column.
     *
     * @param field     path from the root entity, e.g. {@code department.name}; may be omitted for {@code COUNT}
     *                  (= COUNT(*)). For a LIST of objects it names the relation, e.g. {@code trainingRecords}.
     * @param aggregate optional aggregate; non-aggregated columns of a grouped dataset form the GROUP BY
     * @param label     optional header override
     * @param dateGroup optional period for date fields, e.g. {@code MONTH} → one row per month
     * @param fields    LIST of objects: field paths relative to the relation, e.g. {@code ["training.name", "score"]}
     * @param where     per-root aggregates: filters the related rows, relative to the relation
     * @param limit     LIST: max elements (default 100, at most 1000); longer lists are cut and reported as truncated
     * @param order     LIST: element order, ascending by default
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Column(String field, Aggregate aggregate, String label, DateGroup dateGroup,
                         List<String> fields, @Valid Condition where, Integer limit, Sort.Direction order) {

        public Column(String field, Aggregate aggregate, String label) {
            this(field, aggregate, label, null, null, null, null, null);
        }

        public Column(String field, Aggregate aggregate, String label, DateGroup dateGroup) {
            this(field, aggregate, label, dateGroup, null, null, null, null);
        }

        public static Column of(String field) {
            return new Column(field, null, null, null);
        }

        public static Column of(String field, DateGroup dateGroup) {
            return new Column(field, null, null, dateGroup);
        }

        public static Column of(Aggregate aggregate, String field) {
            return new Column(field, aggregate, null, null);
        }

        public static Column count() {
            return new Column(null, Aggregate.COUNT, null, null);
        }

        /** LIST of objects: one object per related row with the given fields. */
        public static Column objects(String relation, String... fields) {
            return new Column(relation, Aggregate.LIST, null, null, List.of(fields), null, null, null);
        }

        public Column where(Condition childFilter) {
            return new Column(field, aggregate, label, dateGroup, fields, childFilter, limit, order);
        }

        public Column limit(int maxElements) {
            return new Column(field, aggregate, label, dateGroup, fields, where, maxElements, order);
        }

        public Column order(Sort.Direction direction) {
            return new Column(field, aggregate, label, dateGroup, fields, where, limit, direction);
        }
    }

    /**
     * A node of a condition tree: a group, a predicate, a field comparison or an EXISTS test.
     * <pre>{@code
     * group:        {"logic": "OR", "conditions": [ ... ]}                     (logic defaults to AND)
     * predicate:    {"field": "salary", "operator": "GT", "value": 50000}
     * date part:    {"field": "hireDate", "dateGroup": "YEAR", "operator": "GTE", "value": 2020}
     * in having:    {"field": "salary", "aggregate": "AVG", "operator": "GT", "value": 50000}
     * two fields:   {"field": "personnel.department.id", "operator": "NOT_EQ", "otherField": "project.department.id"}
     * vs aggregate: {"field": "salary", "operator": "GT", "otherField": "department.members.salary", "otherAggregate": "AVG"}
     * exists:       {"relation": "trainingRecords", "operator": "NOT_EXISTS",
     *                "match": {"field": "training.name", "operator": "EQ", "value": "Fire Safety"}}
     * }</pre>
     *
     * @param otherField     compare with this field instead of a value (EQ, NOT_EQ, LT, LTE, GT, GTE)
     * @param otherAggregate aggregate the other field first: over a related group when its path crosses a
     *                       one-to-many relation (salary vs. its department's average), over the whole root
     *                       table when it is a plain root field (salary vs. the company average)
     * @param otherWhere     filters the rows the other aggregate runs over (relative to that group / the root)
     * @param relation       EXISTS / NOT_EXISTS: path to a one-to-many relation, e.g. {@code trainingRecords}
     * @param match          EXISTS / NOT_EXISTS: optional condition on the related rows, relative to the relation
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Condition(Logic logic, List<@Valid Condition> conditions, String field, Aggregate aggregate,
                            FilterOperator operator, Object value, DateGroup dateGroup, String otherField,
                            Aggregate otherAggregate, @Valid Condition otherWhere, String relation,
                            @Valid Condition match) {

        public enum Logic { AND, OR }

        @JsonIgnore
        public boolean isGroup() {
            return conditions != null;
        }

        public static Condition and(Condition... conditions) {
            return new Condition(Logic.AND, List.of(conditions), null, null, null, null, null, null, null, null, null, null);
        }

        public static Condition or(Condition... conditions) {
            return new Condition(Logic.OR, List.of(conditions), null, null, null, null, null, null, null, null, null, null);
        }

        public static Condition where(String field, FilterOperator operator, Object value) {
            return new Condition(null, null, field, null, operator, value, null, null, null, null, null, null);
        }

        public static Condition where(String field, DateGroup dateGroup, FilterOperator operator, Object value) {
            return new Condition(null, null, field, null, operator, value, dateGroup, null, null, null, null, null);
        }

        public static Condition where(Aggregate aggregate, String field, FilterOperator operator, Object value) {
            return new Condition(null, null, field, aggregate, operator, value, null, null, null, null, null, null);
        }

        public static Condition compare(String field, FilterOperator operator, String otherField) {
            return new Condition(null, null, field, null, operator, null, null, otherField, null, null, null, null);
        }

        /** {@code field <operator> aggregate(otherField)}, e.g. salary above the department average. */
        public static Condition compare(String field, FilterOperator operator, Aggregate otherAggregate,
                                        String otherField, Condition otherWhere) {
            return new Condition(null, null, field, null, operator, null, null, otherField, otherAggregate, otherWhere,
                    null, null);
        }

        public static Condition exists(String relation, Condition match) {
            return new Condition(null, null, null, null, FilterOperator.EXISTS, null, null, null, null, null, relation, match);
        }

        public static Condition notExists(String relation, Condition match) {
            return new Condition(null, null, null, null, FilterOperator.NOT_EXISTS, null, null, null, null, null, relation, match);
        }
    }

    /**
     * Sort by a field path, optionally aggregated or date-grouped (must then match a grouped column);
     * {@code direction} defaults to ascending.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Sort(String field, Aggregate aggregate, Direction direction, DateGroup dateGroup) {

        public enum Direction { ASC, DESC }

        public Sort {
            direction = direction == null ? Direction.ASC : direction;
        }

        public Sort(String field, Aggregate aggregate, Direction direction) {
            this(field, aggregate, direction, null);
        }

        public static Sort asc(String field) {
            return new Sort(field, null, Direction.ASC, null);
        }

        public static Sort desc(String field) {
            return new Sort(field, null, Direction.DESC, null);
        }
    }

    // ==== vocabulary ======================================================================================

    /** Coarse data type of a field; drives which filters and editors the UI offers. */
    public enum FieldType {
        TEXT, NUMBER, DATE, DATE_TIME, BOOLEAN;

        public static FieldType of(Class<?> javaType) {
            if (javaType == LocalDate.class) {
                return DATE;
            }
            if (javaType == LocalDateTime.class || javaType == Instant.class
                    || javaType == OffsetDateTime.class || javaType == ZonedDateTime.class) {
                return DATE_TIME;
            }
            if (javaType == Boolean.class || javaType == boolean.class) {
                return BOOLEAN;
            }
            if (Number.class.isAssignableFrom(javaType) || (javaType.isPrimitive() && javaType != char.class)) {
                return NUMBER;
            }
            return TEXT;
        }
    }

    /** Aggregate functions. Using any of them in a column turns the dataset into a grouped one. */
    public enum Aggregate {
        COUNT("Count"),
        COUNT_DISTINCT("Distinct count"),
        SUM("Sum"),
        AVG("Average"),
        MIN("Minimum"),
        MAX("Maximum"),
        /**
         * Distinct values as a sorted array, e.g. a person's skills: {@code ["Java", "SQL"]}. Turns a one-to-many
         * relation into a nested list on the parent row instead of one row per child. Only usable as a column.
         */
        LIST("List");

        private final String label;

        Aggregate(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Aggregates whose result changes when rows are duplicated by a one-to-many join. */
        public boolean isSensitiveToDuplicates() {
            return this == COUNT || this == SUM || this == AVG;
        }
    }

    /** Filter operators. Nothing outside this list ever reaches the SQL. */
    public enum FilterOperator {
        EQ("="), NOT_EQ("<>"), LT("<"), LTE("<="), GT(">"), GTE(">="),
        /** value: [from, to], both inclusive */
        BETWEEN(null),
        /** value: list of values */
        IN(null), NOT_IN(null),
        /** Text contains the value; wildcards in the value are escaped. */
        CONTAINS(null), STARTS_WITH(null),
        /** No value. */
        IS_NULL(null), IS_NOT_NULL(null),
        /** Date between today and today + N days, e.g. "renewal due within 30 days". */
        NEXT_N_DAYS(null),
        /** Date between today - N days and today. */
        LAST_N_DAYS(null),
        /** At least one related row (via "relation") matches the optional "match" condition. */
        EXISTS(null),
        /** No related row matches, e.g. people WITHOUT a Fire Safety training. */
        NOT_EXISTS(null);

        private final String comparison;

        FilterOperator(String comparison) {
            this.comparison = comparison;
        }

        /** SQL comparison operator, or {@code null} for operators with their own rendering. */
        public String comparison() {
            return comparison;
        }
    }

    /**
     * Groups a date field into periods. Timeline ({@link #YEAR}, {@link #QUARTER}, {@link #MONTH}, {@link #DAY}):
     * each period is distinct; QUARTER/MONTH return the first day of the period as a DATE so they sort
     * chronologically. Seasonality ({@link #QUARTER_OF_YEAR}, {@link #MONTH_OF_YEAR}): a number that ignores the
     * year. The SQL ({@code EXTRACT}, {@code DATE_TRUNC}, {@code CAST}) works on both H2 and PostgreSQL.
     */
    public enum DateGroup {
        YEAR("Year", FieldType.NUMBER, "EXTRACT(YEAR FROM %s)"),
        QUARTER("Quarter", FieldType.DATE, "CAST(DATE_TRUNC('QUARTER', %s) AS DATE)"),
        MONTH("Month", FieldType.DATE, "CAST(DATE_TRUNC('MONTH', %s) AS DATE)"),
        DAY("Day", FieldType.DATE, "CAST(%s AS DATE)"),
        QUARTER_OF_YEAR("Quarter of year", FieldType.NUMBER, "EXTRACT(QUARTER FROM %s)"),
        MONTH_OF_YEAR("Month of year", FieldType.NUMBER, "EXTRACT(MONTH FROM %s)");

        private final String label;
        private final FieldType resultType;
        private final String template;

        DateGroup(String label, FieldType resultType, String template) {
            this.label = label;
            this.resultType = resultType;
            this.template = template;
        }

        public String label() {
            return label;
        }

        public FieldType resultType() {
            return resultType;
        }

        /** {@code expression} is always a catalog column ({@code t0.hire_date}), never user input. */
        String apply(String expression) {
            return template.formatted(expression);
        }
    }

    /** File formats a dataset is exported to. Excel is what business users open; CSV for other systems. */
    public enum ExportFormat {
        XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"),
        CSV("text/csv", ".csv");

        private final String contentType;
        private final String extension;

        ExportFormat(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        public String contentType() {
            return contentType;
        }

        /** With the dot: {@code .xlsx}. */
        public String extension() {
            return extension;
        }

        @JsonValue
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** {@code xlsx} / {@code csv}, any case. */
        @JsonCreator
        public static ExportFormat of(String value) {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        }
    }

    // ==== results =========================================================================================

    /**
     * Header of an output column: what was asked for, its display label and the type of its values.
     * With {@code dateGroup} = MONTH/QUARTER the values are period start dates, for the UI to format.
     *
     * @param keys      LIST of objects: the keys of each object, else null
     * @param listLimit LIST: max elements per cell, else null
     */
    public record ResultColumn(String field, Aggregate aggregate, DateGroup dateGroup, String label, FieldType type,
                               List<String> keys, Integer listLimit) {
    }

    /**
     * One page of tabular output.
     *
     * @param count          rows on this page
     * @param hasNext        whether another page follows (free: one extra row is fetched)
     * @param total          rows in the whole dataset, only when asked for (else {@code null}); a separate
     *                       {@code COUNT(*)} runs only when there is a next page
     * @param truncatedLists labels of LIST columns where at least one cell hit its limit and was cut
     * @param indexHints     columns this dataset used that no index serves, with suggested DDL (empty if all good)
     */
    public record Result(List<ResultColumn> columns, List<List<Object>> rows, int count, int page, int size,
                         boolean hasNext, Long total, List<String> truncatedLists, List<String> indexHints) {
    }

    /**
     * Generated SQL. Identifiers come only from the catalog and generated aliases; every user value is a {@code ?}
     * parameter.
     *
     * @param columns         output columns, in the same order as the SELECT list
     * @param countSql        {@code SELECT COUNT(*)} over the same rows (no ORDER BY)
     * @param countParameters parameters of {@code countSql} (the data query may have more, e.g. in LIST subqueries)
     * @param columnUsages    columns used for joins, filters and sorting; input for index advice
     */
    public record Query(String sql, List<Object> parameters, List<ResultColumn> columns, String countSql,
                        List<Object> countParameters, List<ColumnUsage> columnUsages) {
    }

    /** How a generated query uses a column; collected while the SQL is built, the input for index advice. */
    public record ColumnUsage(String table, String column, Kind kind) {

        public enum Kind {
            /** Join key of a navigation. */
            JOIN,
            /** Plain comparison in WHERE (=, <, IN, BETWEEN, IS NULL, STARTS_WITH, ...): a B-tree index helps. */
            FILTER,
            /** Filter through a date function (YEAR, MONTH, ...): a plain index on the column is not used. */
            FILTER_EXPRESSION,
            /** LIKE '%...%': a B-tree index is not used; needs a trigram / full-text index. */
            FILTER_CONTAINS,
            /** ORDER BY of a detail (non-grouped) dataset: an index can avoid sorting. */
            SORT
        }
    }

    // ==== who asks, and what they may see =================================================================

    /**
     * Who runs a dataset. The host's own user type can implement it directly (no mapping needed); otherwise use
     * {@link #of}. Field permissions use {@link #hasAuthority}; {@link RowPolicy row policies} decide rows.
     */
    public interface Viewer {

        String username();

        boolean hasAuthority(String authority);

        /** Background jobs acting as the system: every field, no row restrictions. */
        default boolean unrestricted() {
            return false;
        }

        static Viewer of(String username, Collection<String> authorities) {
            return new User(username, Set.copyOf(authorities), false);
        }

        /** The engine itself (scheduled exports...): sees everything, so guard what it produces. */
        static Viewer system(String purpose) {
            return new User("system:" + purpose, Set.of(), true);
        }
    }

    /** The default {@link Viewer}. */
    public record User(String username, Set<String> authorities, boolean unrestricted) implements Viewer {

        @Override
        public boolean hasAuthority(String authority) {
            return unrestricted || authorities.contains(authority);
        }
    }

    /**
     * Row-level security hook. The module that owns the data knows the rules (e.g. "users only see their own
     * department"), so it provides an implementation as a Spring bean; every query (and every nested scope of it)
     * gets the returned restrictions.
     */
    @FunctionalInterface
    public interface RowPolicy {

        /**
         * @return restrictions to enforce for this viewer; empty for unrestricted access
         * @throws AccessDeniedException if the viewer may not see any rows at all
         */
        List<RowRestriction> restrictionsFor(Viewer viewer);
    }

    /**
     * "Rows of {@code entity} are visible only if {@code field} matches {@code value}". Applied to every occurrence
     * of the entity in a dataset and to every entity that declares it as {@link DatasetReference#owner() owner}.
     * On the root it filters rows (WHERE); on joined data it hides the related rows (JOIN ... ON).
     *
     * @param entity logical entity name, e.g. {@code Personnel}
     * @param field  Java field name, need not be a dataset field, e.g. {@code departmentId}
     * @param value  a single value, or a collection for "any of"
     */
    public record RowRestriction(String entity, String field, Object value) {
    }

    // ==== errors ==========================================================================================

    /** The definition cannot be turned into a query (unknown field, wrong operator, bad value...). HTTP 400/422. */
    public static class InvalidException extends RuntimeException {
        public InvalidException(String message) {
            super(message);
        }
    }

    /** The viewer is not allowed to use a field, or to see any rows. HTTP 403. */
    public static class AccessDeniedException extends RuntimeException {
        public AccessDeniedException(String message) {
            super(message);
        }
    }

    /** A background query was stopped through its {@link DatasetExecutor.QueryHandle}. */
    public static class CancelledException extends RuntimeException {
        public CancelledException() {
            super("The query was cancelled");
        }
    }
}
