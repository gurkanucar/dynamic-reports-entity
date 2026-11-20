package com.gucardev.dynamicreportsentity.reporting.query;

import com.gucardev.dynamicreportsentity.reporting.ReportAccessDeniedException;
import com.gucardev.dynamicreportsentity.reporting.ReportRowPolicy;
import com.gucardev.dynamicreportsentity.reporting.ReportViewer;
import com.gucardev.dynamicreportsentity.reporting.RowRestriction;
import com.gucardev.dynamicreportsentity.reporting.catalog.CatalogField;
import com.gucardev.dynamicreportsentity.reporting.catalog.ColumnUsage;
import com.gucardev.dynamicreportsentity.reporting.catalog.FieldType;
import com.gucardev.dynamicreportsentity.reporting.catalog.Navigation;
import com.gucardev.dynamicreportsentity.reporting.catalog.ReportCatalog;
import com.gucardev.dynamicreportsentity.reporting.catalog.ReportableEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;

/**
 * Turns a {@link ReportDefinition} into SQL.
 * <ul>
 *   <li><b>Joins</b> come from the paths: every distinct path prefix ({@code manager},
 *       {@code manager.department}) becomes one aliased LEFT JOIN of the named navigation.</li>
 *   <li><b>Grouping</b>: as soon as a column is aggregated, all non-aggregated columns form the GROUP BY.</li>
 *   <li><b>Per-root mode</b>: aggregates over one-to-many relations (LIST, COUNT of children...) become
 *       correlated subqueries, one row per root entity, evaluated only for the rows of the page.
 *       Automatic when the only aggregates are LISTs; explicit with {@code "perRoot": true}.</li>
 *   <li><b>Child scopes</b>: LIST columns, per-root aggregates and EXISTS / NOT_EXISTS conditions open a
 *       nested scope on the far side of a one-to-many relation, with its own joins, filters and row
 *       restrictions.</li>
 *   <li><b>Fan-out protection</b>: combining two independent one-to-many navigations, or summing a
 *       value that a one-to-many join duplicates, is rejected instead of returning wrong numbers.</li>
 *   <li><b>Access control</b>: every field used anywhere is permission-checked; row restrictions are
 *       appended in the main query and in every nested scope.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ReportQueryBuilder {

    static final int MAX_PATH_DEPTH = 4;
    static final int MAX_CONDITIONS = 50;
    static final int MAX_NESTING = 5;
    static final int MAX_LIST_VALUES = 1000;
    static final int DEFAULT_LIST_LIMIT = 100;
    static final int MAX_LIST_LIMIT = 1000;
    static final int MAX_OBJECT_FIELDS = 10;
    /** Object-list keys end up as SQL string literals; only catalog-shaped paths are accepted. */
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*");
    private static final Set<FilterOperator> COMPARISONS = EnumSet.of(FilterOperator.EQ, FilterOperator.NOT_EQ,
            FilterOperator.LT, FilterOperator.LTE, FilterOperator.GT, FilterOperator.GTE);

    private final ReportCatalog catalog;
    private final Clock clock;
    private final List<ReportRowPolicy> rowPolicies;
    private final SqlDialect dialect;

    public ReportQuery build(ReportDefinition definition, ReportViewer viewer) {
        ReportableEntity root = catalog.entity(definition.root())
                .orElseThrow(() -> new InvalidReportException("Unknown entity: " + definition.root()));
        return new Assembly(root, viewer, "t", new int[]{0}, null, null).assemble(definition);
    }

    /** State of one query (the main one, or a nested child scope): the join tree grows while paths are resolved. */
    private final class Assembly {

        private final ReportViewer viewer;
        private final String aliasPrefix;
        private final int[] scopeCounter;
        private final JoinNode root;
        private final Map<String, JoinNode> nodes = new LinkedHashMap<>();
        private final List<Measure> measures = new ArrayList<>();
        private final Set<ColumnUsage> usages = new LinkedHashSet<>();
        /** Parameters of correlated subqueries in the SELECT list, i.e. before FROM. */
        private final List<Object> selectParameters = new ArrayList<>();
        private final List<DerivedJoin> derivedJoins = new ArrayList<>();
        private final Map<String, Measure> groupAggregates = new HashMap<>();
        private List<RowRestriction> restrictions;
        private int conditionCount;

        Assembly(ReportableEntity rootEntity, ReportViewer viewer, String aliasPrefix, int[] scopeCounter,
                 List<RowRestriction> restrictions, String rootLabel) {
            this.viewer = viewer;
            this.aliasPrefix = aliasPrefix;
            this.scopeCounter = scopeCounter;
            this.restrictions = restrictions;
            this.root = new JoinNode("", rootEntity, aliasPrefix + "0", null, null, rootLabel);
            nodes.put("", root);
        }

        ReportQuery assemble(ReportDefinition definition) {
            List<ReportColumn> requested = definition.columns();
            boolean perRoot = decidePerRoot(definition);

            List<Measure> columns = new ArrayList<>();
            for (ReportColumn column : requested) {
                if (perRoot && column.aggregate() != null) {
                    columns.add(childAggregate(column.field(), column.dateGroup(), column.aggregate(), column.fields(),
                            column.where(), column.limit(), column.order(), selectParameters));
                } else {
                    if (column.fields() != null || column.where() != null || column.order() != null) {
                        throw new InvalidReportException("'fields', 'where' and 'order' on a column need per-root mode: "
                                + "drop the other aggregates or set \"perRoot\": true");
                    }
                    Measure measure = measure(column.field(), column.dateGroup(), column.aggregate());
                    columns.add(column.aggregate() == Aggregate.LIST ? measure.withListLimit(listLimit(column.limit())) : measure);
                }
            }
            boolean grouped = !perRoot && columns.stream().anyMatch(Measure::aggregated);
            List<String> groupBy = grouped
                    ? columns.stream().filter(column -> !column.aggregated()).map(Measure::sql).distinct().toList()
                    : List.of();

            List<String> where = new ArrayList<>();
            List<Object> whereParameters = new ArrayList<>();
            if (definition.where() != null) {
                where.add(render(definition.where(), false, perRoot, whereParameters, 1));
            }

            String having = null;
            List<Object> havingParameters = new ArrayList<>();
            if (definition.having() != null) {
                if (perRoot) {
                    // per-root aggregates are plain subquery values, so "having" is just more WHERE
                    where.add(render(definition.having(), true, true, whereParameters, 1));
                } else if (!grouped) {
                    throw new InvalidReportException("'having' needs at least one aggregated column");
                } else {
                    having = render(definition.having(), true, false, havingParameters, 1);
                }
            }

            List<String> orderBy = new ArrayList<>();
            List<Object> orderParameters = new ArrayList<>();
            Set<String> ordered = new HashSet<>();
            for (ReportSort sort : definition.sort()) {
                if (sort.aggregate() == Aggregate.LIST) {
                    throw new InvalidReportException("A LIST column cannot be sorted; use \"order\" on the column to sort its elements");
                }
                List<Object> sortParameters = new ArrayList<>();
                Measure measure = perRoot && sort.aggregate() != null
                        ? perRootFilterValue(sort.field(), sort.dateGroup(), sort.aggregate(), sortParameters)
                        : measure(sort.field(), sort.dateGroup(), sort.aggregate());
                if (grouped && !measure.aggregated() && !groupBy.contains(measure.sql())) {
                    throw new InvalidReportException("Cannot sort by '" + sort.field()
                            + "': in a grouped report only grouped columns or aggregates can be sorted");
                }
                if (!grouped && !perRoot && measure.aggregated()) {
                    throw new InvalidReportException("Sorting by an aggregate needs aggregated columns");
                }
                if (ordered.add(measure.sql())) {
                    orderBy.add(measure.sql() + " " + sort.direction());
                    orderParameters.addAll(sortParameters);
                }
                if (!grouped && sort.dateGroup() == null && measure.ref() != null) {
                    use(measure.ref(), ColumnUsage.Kind.SORT);
                }
            }

            checkFanOut(grouped);
            applyRowRestrictions(where, whereParameters);

            // Paging needs a stable order: user's sort, then whatever makes a row unique.
            List<String> tieBreakers = grouped ? groupBy : nodes.values().stream()
                    .flatMap(node -> node.entity.idColumns().stream().map(column -> node.alias + "." + column))
                    .toList();
            tieBreakers.stream().filter(ordered::add).forEach(orderBy::add);

            // Everything after SELECT and before ORDER BY; shared by the data and the count query.
            List<Object> parameters = new ArrayList<>();
            StringBuilder sql = new StringBuilder("\n").append(fromClause(parameters, "\n"));
            if (!where.isEmpty()) {
                sql.append("\nWHERE ").append(String.join("\n  AND ", where));
                parameters.addAll(whereParameters);
            }
            if (!groupBy.isEmpty()) {
                sql.append("\nGROUP BY ").append(String.join(", ", groupBy));
            }
            if (having != null) {
                sql.append("\nHAVING ").append(having);
                parameters.addAll(havingParameters);
            }
            String body = sql.toString();
            String dataSql = "SELECT " + columns.stream().map(Measure::sql).collect(joining(", ")) + body
                    + (orderBy.isEmpty() ? "" : "\nORDER BY " + String.join(", ", orderBy));
            // Detail reports: the row count does not depend on the SELECT list. Grouped reports keep their
            // columns, with unique aliases (derived tables must not have duplicate names like t0.name, t1.name).
            List<String> aliased = new ArrayList<>();
            for (int i = 0; i < columns.size(); i++) {
                aliased.add(columns.get(i).sql() + " AS c" + i);
            }
            String countSql = "SELECT COUNT(*) FROM (SELECT " + (grouped ? String.join(", ", aliased) : "1 AS c0")
                    + body + "\n) report";
            List<Object> countParameters = List.copyOf(parameters);
            List<Object> dataParameters = new ArrayList<>(selectParameters);
            dataParameters.addAll(parameters);
            dataParameters.addAll(orderParameters);

            List<ResultColumn> resultColumns = new ArrayList<>();
            for (int i = 0; i < columns.size(); i++) {
                ReportColumn column = requested.get(i);
                Measure measure = columns.get(i);
                String label = column.label() == null || column.label().isBlank() ? measure.label() : column.label();
                resultColumns.add(new ResultColumn(column.field(), column.aggregate(), column.dateGroup(), label,
                        measure.type(), measure.keys(), measure.listLimit()));
            }
            return new ReportQuery(dataSql, Collections.unmodifiableList(dataParameters), List.copyOf(resultColumns),
                    countSql, countParameters, List.copyOf(usages));
        }

        /**
         * Per-root mode turns aggregates over one-to-many relations into correlated subqueries. Automatic
         * when the only aggregates are LISTs; explicit with "perRoot": true for COUNT/SUM/... of children.
         */
        private boolean decidePerRoot(ReportDefinition definition) {
            List<ReportColumn> aggregated = definition.columns().stream().filter(column -> column.aggregate() != null).toList();
            if (aggregated.isEmpty() || Boolean.FALSE.equals(definition.perRoot())) {
                return false;
            }
            boolean mainFansOut = touchesToMany(definition.where())
                    || definition.columns().stream().anyMatch(column -> column.aggregate() == null && pathHasToMany(column.field()))
                    || definition.sort().stream().anyMatch(sort -> sort.aggregate() == null && pathHasToMany(sort.field()));
            if (Boolean.TRUE.equals(definition.perRoot())) {
                for (ReportColumn column : aggregated) {
                    String path = column.fields() != null && !column.fields().isEmpty() ? column.field() + ".x" : column.field();
                    if (!pathHasToMany(path)) {
                        throw new InvalidReportException("perRoot needs every aggregate to go through a one-to-many relation "
                                + "(e.g. COUNT of trainingRecords.status); '" + (column.field() == null ? "COUNT(*)" : column.field())
                                + "' is not. COUNT(*) and aggregates of the root's own fields only make sense when grouping.");
                    }
                }
                if (mainFansOut) {
                    throw new InvalidReportException("perRoot cannot be combined with plain columns, filters or sorting through a "
                            + "one-to-many relation; filter by related rows with EXISTS / NOT_EXISTS instead");
                }
                return true;
            }
            boolean onlyLists = aggregated.stream().allMatch(column -> column.aggregate() == Aggregate.LIST);
            return onlyLists && !mainFansOut && definition.having() == null;
        }

        // ---- child scopes: correlated subqueries over a one-to-many relation -----------------------

        /**
         * The far side of the first one-to-many hop of a path, as its own nested query.
         *
         * @param innerKey column of the nested query that points back ({@code s1_0.department_id})
         * @param outerKey column it matches in this query ({@code t1.id}); correlation = innerKey = outerKey
         */
        private record ChildScope(Assembly sub, String remaining, String correlation, String label,
                                  String innerKey, String outerKey) {
        }

        /** A pre-aggregated derived table joined into this query (see {@link #groupAggregateJoin}). */
        private record DerivedJoin(String sql, List<Object> parameters) {
        }

        /**
         * Splits {@code path} at its first one-to-many hop. To-one hops before it are joined in this query;
         * from the hop on, everything lives in a nested {@link Assembly} correlated to this query.
         *
         * @param endsAtRelation true when the path names a relation (EXISTS, object lists), false when it ends at a field
         */
        private ChildScope childScope(String path, boolean endsAtRelation) {
            if (path == null || path.isBlank()) {
                throw new InvalidReportException("A relation path is required");
            }
            String[] parts = path.split("\\.", -1);
            int relationCount = endsAtRelation ? parts.length : parts.length - 1;
            if (relationCount > MAX_PATH_DEPTH) {
                throw new InvalidReportException("Path '" + path + "' is too deep (max " + MAX_PATH_DEPTH + " relations)");
            }
            JoinNode anchor = root;
            int i = 0;
            for (; i < relationCount; i++) {
                Navigation navigation = anchor.entity.navigations().get(parts[i]);
                if (navigation == null) {
                    throw new InvalidReportException("Unknown relation '" + parts[i] + "' on " + anchor.label()
                            + " (in '" + path + "')");
                }
                if (navigation.toMany()) {
                    break;
                }
                anchor = child(anchor, parts[i], path);
            }
            if (i == relationCount) {
                throw new InvalidReportException("'" + path + "' needs a path through a one-to-many relation, e.g. members.fullName");
            }
            Navigation navigation = anchor.entity.navigations().get(parts[i]);
            ReportableEntity target = catalog.entity(navigation.targetEntity()).orElseThrow();
            String label = anchor.label() + " / " + navigation.label();
            Assembly sub = new Assembly(target, viewer, "s" + (++scopeCounter[0]) + "_", scopeCounter, restrictions(), label);
            usages.add(new ColumnUsage(target.table(), navigation.joinColumn(), ColumnUsage.Kind.JOIN));
            String remaining = String.join(".", Arrays.copyOfRange(parts, i + 1, parts.length));
            String innerKey = sub.root.alias + "." + navigation.joinColumn();
            String outerKey = anchor.alias + "." + navigation.parentColumn();
            return new ChildScope(sub, remaining, innerKey + " = " + outerKey, label, innerKey, outerKey);
        }

        /**
         * {@code FROM ... [LEFT JOIN ...] WHERE <correlation> AND <conditions> AND <row restrictions>} of a child
         * scope. Call it after everything inside the scope has been resolved; its parameters go to {@code out}.
         */
        private String subqueryBody(ChildScope scope, List<String> conditions, List<Object> conditionParameters,
                                    List<Object> out) {
            List<String> where = new ArrayList<>();
            where.add(scope.correlation());
            where.addAll(conditions);
            List<Object> whereParameters = new ArrayList<>(conditionParameters);
            scope.sub().applyRowRestrictions(where, whereParameters);
            List<Object> fromParameters = new ArrayList<>();
            String from = scope.sub().fromClause(fromParameters, " ");
            usages.addAll(scope.sub().usages);
            out.addAll(fromParameters);
            out.addAll(whereParameters);
            return from + " WHERE " + String.join(" AND ", where);
        }

        /**
         * An aggregate over children, as a scalar subquery per root row:
         * <ul>
         *   <li>LIST of a field: {@code (SELECT ARRAY_AGG(v ORDER BY v) FROM (SELECT DISTINCT x AS v ... LIMIT n+1) s)}</li>
         *   <li>LIST of objects ({@code fields}): the same with {@code JSON_ARRAYAGG(JSON_OBJECT(...))}</li>
         *   <li>COUNT, COUNT_DISTINCT, SUM, AVG, MIN, MAX: {@code (SELECT SUM(x) FROM ... WHERE ...)}</li>
         * </ul>
         * {@code where} filters the children, relative to the entity behind the one-to-many hop.
         */
        private Measure childAggregate(String path, DateGroup dateGroup, Aggregate aggregate, List<String> fields,
                                       ReportCondition where, Integer limit, ReportSort.Direction order, List<Object> out) {
            boolean objects = aggregate == Aggregate.LIST && fields != null && !fields.isEmpty();
            if (path == null || path.isBlank()) {
                throw new InvalidReportException("A field is required for " + aggregate + " in per-root mode");
            }
            ChildScope scope = childScope(path, objects);
            Assembly sub = scope.sub();
            List<String> conditions = new ArrayList<>();
            List<Object> conditionParameters = new ArrayList<>();
            if (where != null) {
                conditions.add(sub.render(where, false, false, conditionParameters, 1));
            }

            if (aggregate != Aggregate.LIST) {
                if (limit != null || order != null || fields != null) {
                    throw new InvalidReportException("'limit', 'order' and 'fields' only apply to LIST");
                }
                Measure measure = sub.measure(scope.remaining(), dateGroup, aggregate);
                sub.checkFanOut(true);
                String body = subqueryBody(scope, conditions, conditionParameters, out);
                return Measure.of(null, aggregate, "(SELECT " + measure.sql() + " " + body + ")", measure.type(), measure.label());
            }

            // The limit is applied while reading the row: limiting inside the subquery would need a derived
            // table referencing the outer row, which H2 does not support. The response stays small; the
            // database still builds the full list.
            int n = listLimit(limit);
            String direction = order == ReportSort.Direction.DESC ? " DESC" : "";
            if (!objects) {
                Measure value = sub.measure(scope.remaining(), dateGroup, null);
                conditions.add(value.sql() + " IS NOT NULL");
                String body = subqueryBody(scope, conditions, conditionParameters, out);
                String sql = "(SELECT ARRAY_AGG(DISTINCT " + value.sql() + " ORDER BY " + value.sql() + direction + ") "
                        + body + ")";
                return new Measure(null, Aggregate.LIST, sql, value.type(), value.label() + " (list)", n, null);
            }

            if (fields.size() > MAX_OBJECT_FIELDS) {
                throw new InvalidReportException("A LIST of objects takes at most " + MAX_OBJECT_FIELDS + " fields");
            }
            List<String> keys = new ArrayList<>();
            List<String> notNull = new ArrayList<>();
            List<String> orderBy = new ArrayList<>();
            for (String field : fields) {
                if (field == null || !SAFE_KEY.matcher(field).matches()) {
                    throw new InvalidReportException("Unknown field: " + field);
                }
                Measure value = sub.measure(field, null, null);
                keys.add("'" + field + "' VALUE " + value.sql());
                notNull.add(value.sql() + " IS NOT NULL");
                orderBy.add(value.sql() + direction);
            }
            // One object per related row: its fields must not fan out again, or objects would repeat.
            if (sub.nodes.values().stream().anyMatch(node -> node.navigation != null && node.navigation.toMany())) {
                throw new InvalidReportException("The fields of a LIST of objects must be reachable through to-one relations"
                        + " from '" + path + "'");
            }
            conditions.add("(" + String.join(" OR ", notNull) + ")");
            String body = subqueryBody(scope, conditions, conditionParameters, out);
            String sql = "(SELECT CAST(JSON_ARRAYAGG(JSON_OBJECT(" + String.join(", ", keys) + ") ORDER BY "
                    + String.join(", ", orderBy) + ") AS VARCHAR) " + body + ")";
            return new Measure(null, Aggregate.LIST, sql, FieldType.TEXT, scope.label() + " (list)", n, List.copyOf(fields));
        }

        /**
         * The right-hand side of "field vs. aggregate", as a scalar subquery:
         * <ul>
         *   <li>path through a one-to-many relation ({@code department.members.salary}): the aggregate over
         *       that related group, correlated to the current row – e.g. the person's department average;</li>
         *   <li>plain root field ({@code salary}): the aggregate over the whole root table – e.g. the company
         *       average. Row restrictions apply, so it is the average of what the viewer may see.</li>
         * </ul>
         */
        private Measure aggregateToCompare(String path, Aggregate aggregate, ReportCondition otherWhere, List<Object> parameters) {
            if (aggregate == Aggregate.LIST) {
                throw new InvalidReportException("A LIST cannot be compared; use COUNT, SUM, AVG, MIN or MAX");
            }
            if (path != null && pathHasToMany(path)) {
                return groupAggregateJoin(path, null, aggregate, otherWhere);
            }
            if (path == null || path.contains(".")) {
                throw new InvalidReportException("'otherField' with 'otherAggregate' must cross a one-to-many relation "
                        + "(e.g. department.members.salary) or be a field of " + root.label() + " (whole table)");
            }
            Assembly whole = new Assembly(root.entity, viewer, "s" + (++scopeCounter[0]) + "_", scopeCounter, restrictions(),
                    "All " + root.label());
            Measure measure = whole.measure(path, null, aggregate);
            List<String> where = new ArrayList<>();
            List<Object> whereParameters = new ArrayList<>();
            if (otherWhere != null) {
                where.add(whole.render(otherWhere, false, false, whereParameters, 1));
            }
            whole.applyRowRestrictions(where, whereParameters);
            List<Object> fromParameters = new ArrayList<>();
            String from = whole.fromClause(fromParameters, " ");
            usages.addAll(whole.usages);
            parameters.addAll(fromParameters);
            parameters.addAll(whereParameters);
            String sql = "(SELECT " + measure.sql() + " " + from + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where)) + ")";
            return Measure.of(null, aggregate, sql, measure.type(), measure.label());
        }

        /**
         * Aggregate of a related group, computed once per group instead of once per row:
         * {@code LEFT JOIN (SELECT s1_0.department_id AS k, AVG(s1_0.salary) AS v FROM personnel s1_0
         * GROUP BY s1_0.department_id) g2 ON g2.k = t1.id}, compared as {@code g2.v}. A correlated subquery
         * here would re-scan the whole group for every row (100k people x 2k colleagues).
         */
        private Measure groupAggregateJoin(String path, DateGroup dateGroup, Aggregate aggregate, ReportCondition otherWhere) {
            if (path == null || path.isBlank()) {
                throw new InvalidReportException("A field is required for " + aggregate + " in per-root mode");
            }
            if (aggregate == Aggregate.LIST) {
                throw new InvalidReportException("A LIST cannot be compared or sorted; use COUNT, SUM, AVG, MIN or MAX");
            }
            // the same aggregate in "having" and "sort" (or twice) shares one derived table
            String key = path + "|" + dateGroup + "|" + aggregate + "|" + otherWhere;
            Measure cached = groupAggregates.get(key);
            if (cached != null) {
                return cached;
            }
            ChildScope scope = childScope(path, false);
            Assembly sub = scope.sub();
            List<String> where = new ArrayList<>();
            List<Object> whereParameters = new ArrayList<>();
            if (otherWhere != null) {
                where.add(sub.render(otherWhere, false, false, whereParameters, 1));
            }
            Measure measure = sub.measure(scope.remaining(), dateGroup, aggregate);
            sub.checkFanOut(true);
            sub.applyRowRestrictions(where, whereParameters);
            List<Object> parameters = new ArrayList<>();
            String from = sub.fromClause(parameters, " ");
            parameters.addAll(whereParameters);
            usages.addAll(sub.usages);
            String alias = "g" + (++scopeCounter[0]);
            String derived = "(SELECT " + scope.innerKey() + " AS k, " + measure.sql() + " AS v " + from
                    + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where))
                    + " GROUP BY " + scope.innerKey() + dialect.materializeDerivedTable() + ")";
            derivedJoins.add(new DerivedJoin("LEFT JOIN " + derived + " " + alias + " ON " + alias + ".k = " + scope.outerKey(),
                    parameters));
            // an empty group has no row in the derived table: a count of 0, not NULL
            String value = aggregate == Aggregate.COUNT || aggregate == Aggregate.COUNT_DISTINCT
                    ? "COALESCE(" + alias + ".v, 0)" : alias + ".v";
            Measure result = Measure.of(null, aggregate, value, measure.type(), measure.label());
            groupAggregates.put(key, result);
            return result;
        }

        /**
         * A child aggregate used by a per-root "having" or sort. Unlike the columns (evaluated only for the
         * rows of the page) these are needed for every root row, so where the database supports it they are
         * computed in one pass per group and joined ({@link #groupAggregateJoin}, shared by having and sort);
         * otherwise a correlated subquery. See {@link SqlDialect#preAggregatesPerRootFilters()}.
         */
        private Measure perRootFilterValue(String path, DateGroup dateGroup, Aggregate aggregate, List<Object> parameters) {
            if (aggregate == null) {
                throw new InvalidReportException("A 'having' condition needs an aggregate (field '" + path + "')");
            }
            return dialect.preAggregatesPerRootFilters()
                    ? groupAggregateJoin(path, dateGroup, aggregate, null)
                    : childAggregate(path, dateGroup, aggregate, null, null, null, null, parameters);
        }

        private int listLimit(Integer limit) {
            return Math.clamp(limit == null ? DEFAULT_LIST_LIMIT : limit, 1, MAX_LIST_LIMIT);
        }

        /** Whether any field path in the condition tree makes the main query fan out (EXISTS does not). */
        private boolean touchesToMany(ReportCondition condition) {
            if (condition == null) {
                return false;
            }
            if (condition.isGroup()) {
                return condition.conditions().stream().anyMatch(this::touchesToMany);
            }
            if (condition.relation() != null) {
                return false;
            }
            // an aggregated other side is a subquery and does not fan out the main query
            return pathHasToMany(condition.field())
                    || (condition.otherAggregate() == null && pathHasToMany(condition.otherField()));
        }

        /** Walks the catalog only (no joins are created); unknown names are reported later by resolve(). */
        private boolean pathHasToMany(String path) {
            if (path == null) {
                return false;
            }
            String[] parts = path.split("\\.", -1);
            ReportableEntity entity = root.entity;
            for (int i = 0; i < parts.length - 1; i++) {
                Navigation navigation = entity.navigations().get(parts[i]);
                if (navigation == null) {
                    return false;
                }
                if (navigation.toMany()) {
                    return true;
                }
                entity = catalog.entity(navigation.targetEntity()).orElseThrow();
            }
            return false;
        }

        private void use(FieldRef ref, ColumnUsage.Kind kind) {
            usages.add(new ColumnUsage(ref.node().entity.table(), ref.field().column(), kind));
        }

        /** {@code FROM root [LEFT JOIN ...]}, with row restrictions of joined nodes in their ON clauses. */
        private String fromClause(List<Object> parameters, String separator) {
            StringBuilder sql = new StringBuilder("FROM ").append(root.entity.table()).append(' ').append(root.alias);
            for (JoinNode node : nodes.values()) {
                if (node == root) {
                    continue;
                }
                sql.append(separator).append("LEFT JOIN ").append(node.entity.table()).append(' ').append(node.alias)
                        .append(" ON ").append(node.alias).append('.').append(node.navigation.joinColumn())
                        .append(" = ").append(node.parent.alias).append('.').append(node.navigation.parentColumn());
                node.onConditions.forEach(condition -> sql.append(" AND ").append(condition));
                parameters.addAll(node.onParameters);
                usages.add(new ColumnUsage(node.entity.table(), node.navigation.joinColumn(), ColumnUsage.Kind.JOIN));
            }
            for (DerivedJoin join : derivedJoins) {
                sql.append(separator).append(join.sql());
                parameters.addAll(join.parameters());
            }
            return sql.toString();
        }

        // ---- paths ---------------------------------------------------------------------------

        private FieldRef resolve(String path) {
            String[] parts = path.split("\\.", -1);
            if (parts.length - 1 > MAX_PATH_DEPTH) {
                throw new InvalidReportException("Path '" + path + "' is too deep (max " + MAX_PATH_DEPTH + " relations)");
            }
            JoinNode node = root;
            for (int i = 0; i < parts.length - 1; i++) {
                node = child(node, parts[i], path);
            }
            CatalogField field = node.entity.fields().get(parts[parts.length - 1]);
            if (field == null) {
                throw new InvalidReportException("Unknown field: " + path);
            }
            FieldRef ref = new FieldRef(node, field, path);
            if (!field.isVisibleTo(viewer)) {
                throw new ReportAccessDeniedException("You are not allowed to use field: " + ref.label());
            }
            return ref;
        }

        private JoinNode child(JoinNode parent, String name, String path) {
            String key = parent.path.isEmpty() ? name : parent.path + "." + name;
            JoinNode existing = nodes.get(key);
            if (existing != null) {
                return existing;
            }
            Navigation navigation = parent.entity.navigations().get(name);
            if (navigation == null) {
                throw new InvalidReportException("Unknown relation '" + name + "' on " + parent.label()
                        + " (in '" + path + "')");
            }
            ReportableEntity target = catalog.entity(navigation.targetEntity()).orElseThrow();
            JoinNode node = new JoinNode(key, target, aliasPrefix + nodes.size(), parent, navigation, null);
            nodes.put(key, node);
            return node;
        }

        /** A field, optionally reduced to a date period, optionally aggregated – in that order. */
        private Measure measure(String path, DateGroup dateGroup, Aggregate aggregate) {
            if (path == null || path.isBlank()) {
                if (aggregate != Aggregate.COUNT || dateGroup != null) {
                    throw new InvalidReportException("A field is required"
                            + (aggregate == null ? "" : " for " + aggregate) + (dateGroup == null ? "" : " with " + dateGroup));
                }
                return track(Measure.of(null, aggregate, "COUNT(*)", FieldType.NUMBER, "Count of " + root.label()));
            }
            FieldRef ref = resolve(path);
            String expression = ref.sql();
            FieldType type = ref.field.type();
            String label = ref.label();
            if (dateGroup != null) {
                if (type != FieldType.DATE && type != FieldType.DATE_TIME) {
                    throw new InvalidReportException("Date grouping " + dateGroup + " needs a date field, '"
                            + path + "' is " + type);
                }
                expression = dateGroup.apply(expression);
                type = dateGroup.resultType();
                label = label + " (" + dateGroup.label() + ")";
            }
            if (aggregate == null) {
                return track(Measure.of(ref, null, expression, type, label));
            }
            if ((aggregate == Aggregate.SUM || aggregate == Aggregate.AVG) && type != FieldType.NUMBER) {
                throw new InvalidReportException(aggregate + " needs a numeric field, '" + path + "' is " + type);
            }
            String sql = switch (aggregate) {
                case COUNT_DISTINCT -> "COUNT(DISTINCT " + expression + ")";
                // Grouped mode: distinct, sorted, NULLs from unmatched LEFT JOINs left out (H2 and PostgreSQL).
                case LIST -> "ARRAY_AGG(DISTINCT " + expression + " ORDER BY " + expression + ") FILTER (WHERE "
                        + expression + " IS NOT NULL)";
                default -> aggregate.name() + "(" + expression + ")";
            };
            if (aggregate == Aggregate.LIST) {
                return track(Measure.of(ref, aggregate, sql, type, label + " (list)"));
            }
            FieldType resultType = aggregate == Aggregate.MIN || aggregate == Aggregate.MAX ? type : FieldType.NUMBER;
            return track(Measure.of(ref, aggregate, sql, resultType, aggregate.label() + " of " + label));
        }

        private Measure track(Measure measure) {
            measures.add(measure);
            return measure;
        }

        // ---- conditions ----------------------------------------------------------------------

        /**
         * @param having  whether this is the having clause (aggregates required)
         * @param perRoot whether aggregates are per-root subqueries (then having-conditions render as WHERE)
         */
        private String render(ReportCondition condition, boolean having, boolean perRoot, List<Object> parameters, int depth) {
            if (condition.isGroup()) {
                if (depth > MAX_NESTING) {
                    throw new InvalidReportException("Conditions are nested too deeply (max " + MAX_NESTING + ")");
                }
                if (condition.conditions().isEmpty()) {
                    throw new InvalidReportException("A condition group must not be empty");
                }
                String joiner = " " + (condition.logic() == null ? ReportCondition.Logic.AND : condition.logic()) + " ";
                List<String> parts = new ArrayList<>();
                for (ReportCondition child : condition.conditions()) {
                    parts.add(render(child, having, perRoot, parameters, depth + 1));
                }
                return "(" + String.join(joiner, parts) + ")";
            }
            if (++conditionCount > MAX_CONDITIONS) {
                throw new InvalidReportException("Too many conditions (max " + MAX_CONDITIONS + ")");
            }
            FilterOperator operator = condition.operator();
            if (operator == null) {
                throw new InvalidReportException("Condition on '" + condition.field() + "' needs an operator");
            }

            // EXISTS / NOT_EXISTS: "has at least one / has no related row matching ..."
            if (condition.relation() != null || operator == FilterOperator.EXISTS || operator == FilterOperator.NOT_EXISTS) {
                if (condition.relation() == null || (operator != FilterOperator.EXISTS && operator != FilterOperator.NOT_EXISTS)) {
                    throw new InvalidReportException("EXISTS / NOT_EXISTS need a \"relation\" (and an optional \"match\"), "
                            + "and a \"relation\" needs EXISTS or NOT_EXISTS");
                }
                if (having) {
                    throw new InvalidReportException("EXISTS / NOT_EXISTS belong in 'where'");
                }
                ChildScope scope = childScope(condition.relation(), true);
                List<String> conditions = new ArrayList<>();
                List<Object> matchParameters = new ArrayList<>();
                if (condition.match() != null) {
                    conditions.add(scope.sub().render(condition.match(), false, false, matchParameters, depth + 1));
                }
                String body = subqueryBody(scope, conditions, matchParameters, parameters);
                return (operator == FilterOperator.NOT_EXISTS ? "NOT " : "") + "EXISTS (SELECT 1 " + body + ")";
            }

            if (condition.aggregate() == Aggregate.LIST) {
                throw new InvalidReportException("A LIST cannot be filtered; filter its children with \"where\" on the column, "
                        + "or filter rows by related rows with EXISTS");
            }
            if (having && condition.aggregate() == null) {
                throw new InvalidReportException("Conditions in 'having' must use an aggregate (field '" + condition.field() + "')");
            }
            if (!having && condition.aggregate() != null) {
                throw new InvalidReportException("Aggregates can only be filtered in 'having'");
            }

            // field-to-field comparison: personnel.department.id <> project.department.id
            if (condition.otherField() != null) {
                if (having) {
                    throw new InvalidReportException("Comparing two fields is only supported in 'where'");
                }
                if (!COMPARISONS.contains(operator)) {
                    throw new InvalidReportException("Comparing two fields supports EQ, NOT_EQ, LT, LTE, GT and GTE");
                }
                Measure left = measure(condition.field(), condition.dateGroup(), null);
                Measure right = condition.otherAggregate() == null
                        ? measure(condition.otherField(), condition.dateGroup(), null)
                        : aggregateToCompare(condition.otherField(), condition.otherAggregate(), condition.otherWhere(), parameters);
                if (condition.otherAggregate() == null && condition.otherWhere() != null) {
                    throw new InvalidReportException("'otherWhere' needs an 'otherAggregate'");
                }
                if (left.type() != right.type()) {
                    throw new InvalidReportException("Cannot compare " + left.type() + " '" + condition.field() + "' with "
                            + right.type() + " '" + right.label() + "'");
                }
                use(left.ref(), ColumnUsage.Kind.FILTER);
                if (right.ref() != null) {
                    use(right.ref(), ColumnUsage.Kind.FILTER);
                }
                return left.sql() + " " + operator.comparison() + " " + right.sql();
            }

            Measure measure = having && perRoot
                    ? perRootFilterValue(condition.field(), condition.dateGroup(), condition.aggregate(), parameters)
                    : measure(condition.field(), condition.dateGroup(), condition.aggregate());
            if (!having && measure.ref() != null) {
                use(measure.ref(), condition.dateGroup() != null ? ColumnUsage.Kind.FILTER_EXPRESSION
                        : operator == FilterOperator.CONTAINS ? ColumnUsage.Kind.FILTER_CONTAINS
                        : ColumnUsage.Kind.FILTER);
            }
            return predicate(measure, operator, condition.value(), parameters);
        }

        private String predicate(Measure measure, FilterOperator operator, Object value, List<Object> parameters) {
            String expression = measure.sql();
            return switch (operator) {
                case IS_NULL -> expression + " IS NULL";
                case IS_NOT_NULL -> expression + " IS NOT NULL";
                case IN, NOT_IN -> {
                    List<?> values = listValue(measure, operator, value);
                    if (values.isEmpty() || values.size() > MAX_LIST_VALUES) {
                        throw new InvalidReportException(operator + " needs 1 to " + MAX_LIST_VALUES + " values");
                    }
                    values.forEach(item -> parameters.add(coerce(measure, item)));
                    yield expression + (operator == FilterOperator.IN ? " IN (" : " NOT IN (")
                            + String.join(", ", Collections.nCopies(values.size(), "?")) + ")";
                }
                case BETWEEN -> {
                    List<?> values = listValue(measure, operator, value);
                    if (values.size() != 2) {
                        throw new InvalidReportException("BETWEEN needs exactly two values [from, to]");
                    }
                    parameters.add(coerce(measure, values.get(0)));
                    parameters.add(coerce(measure, values.get(1)));
                    yield expression + " BETWEEN ? AND ?";
                }
                case NEXT_N_DAYS, LAST_N_DAYS -> {
                    requireType(measure, operator, FieldType.DATE, FieldType.DATE_TIME);
                    long days = dayCount(operator, value);
                    LocalDate today = LocalDate.now(clock);
                    parameters.add(operator == FilterOperator.NEXT_N_DAYS ? today : today.minusDays(days));
                    parameters.add(operator == FilterOperator.NEXT_N_DAYS ? today.plusDays(days) : today);
                    yield expression + " BETWEEN ? AND ?";
                }
                case CONTAINS, STARTS_WITH -> {
                    requireType(measure, operator, FieldType.TEXT);
                    String text = escapeLike(String.valueOf(requireValue(measure, value)));
                    parameters.add(operator == FilterOperator.CONTAINS ? "%" + text + "%" : text + "%");
                    yield expression + " LIKE ? ESCAPE '\\'";
                }
                case EXISTS, NOT_EXISTS -> throw new InvalidReportException(operator + " needs a \"relation\"");
                default -> {
                    parameters.add(coerce(measure, value));
                    yield expression + " " + operator.comparison() + " ?";
                }
            };
        }

        // ---- fan-out -------------------------------------------------------------------------

        /**
         * Rejects reports whose numbers would be silently wrong:
         * two independent one-to-many joins multiply each other's rows (chasm trap), and
         * SUM/AVG/COUNT of a value above a one-to-many join counts it once per child row (fan trap).
         * <p>
         * Exception: a grouped report that only uses duplicate-insensitive aggregates (LIST, COUNT_DISTINCT,
         * MIN, MAX) may combine independent one-to-many branches: the multiplied rows collapse into the
         * same distinct values.
         */
        private void checkFanOut(boolean grouped) {
            List<JoinNode> toMany = nodes.values().stream()
                    .filter(node -> node.navigation != null && node.navigation.toMany())
                    .toList();
            boolean duplicateSafe = grouped && measures.stream()
                    .noneMatch(measure -> measure.aggregated() && measure.aggregate().isSensitiveToDuplicates());
            for (int i = 0; i < toMany.size() && !duplicateSafe; i++) {
                for (int j = i + 1; j < toMany.size(); j++) {
                    JoinNode a = toMany.get(i);
                    JoinNode b = toMany.get(j);
                    if (!a.isAncestorOrSelfOf(b) && !b.isAncestorOrSelfOf(a)) {
                        throw new InvalidReportException("'" + a.path + "' and '" + b.path + "' are separate one-to-many"
                                + " relations; combining them would multiply rows. Use LIST / COUNT_DISTINCT / MIN / MAX"
                                + " (or \"perRoot\": true), split the report, or start from another entity.");
                    }
                }
            }
            JoinNode grain = toMany.stream().max(Comparator.comparingInt(JoinNode::depth)).orElse(root);
            for (Measure measure : measures) {
                if (measure.ref() != null && measure.aggregated() && measure.aggregate().isSensitiveToDuplicates()
                        && !grain.isAncestorOrSelfOf(measure.ref().node())) {
                    throw new InvalidReportException(measure.aggregate() + " of '" + measure.ref().path()
                            + "' would be inflated by the one-to-many relation '" + grain.path
                            + "'. Use COUNT_DISTINCT, MIN or MAX, \"perRoot\": true, or start the report from "
                            + grain.entity.label() + ".");
                }
            }
        }

        // ---- row-level security --------------------------------------------------------------

        private void applyRowRestrictions(List<String> where, List<Object> whereParameters) {
            for (RowRestriction restriction : restrictions()) {
                for (JoinNode node : nodes.values()) {
                    List<Object> conditionParameters = new ArrayList<>();
                    String condition = restrictionFor(node.entity, node.alias, restriction, conditionParameters);
                    if (condition == null) {
                        continue;
                    }
                    if (node == root) {
                        where.add(condition);
                        whereParameters.addAll(conditionParameters);
                    } else {
                        node.onConditions.add(condition);
                        node.onParameters.addAll(conditionParameters);
                    }
                }
            }
        }

        /** The viewer's restrictions, asked from the policies once per build and shared with child scopes. */
        private List<RowRestriction> restrictions() {
            if (restrictions == null) {
                // the system viewer (snapshot refresh) sees every row; snapshots are guarded when read
                restrictions = viewer.system() ? List.of()
                        : rowPolicies.stream().flatMap(policy -> policy.restrictionsFor(viewer).stream()).toList();
            }
            return restrictions;
        }

        /**
         * Condition that hides rows of {@code entity} (under {@code alias}) the viewer may not see, or
         * {@code null} when the restriction does not concern this entity.
         */
        private String restrictionFor(ReportableEntity entity, String alias, RowRestriction restriction,
                                      List<Object> parameters) {
            ReportableEntity restricted = catalog.entity(restriction.entity()).orElseThrow(() ->
                    new IllegalStateException("Row restriction on unknown entity " + restriction.entity()));
            String column = restricted.columns().get(restriction.field());
            if (column == null) {
                throw new IllegalStateException("Row restriction on unknown field "
                        + restriction.entity() + "." + restriction.field());
            }
            if (entity.name().equals(restricted.name())) {
                usages.add(new ColumnUsage(restricted.table(), column, ColumnUsage.Kind.FILTER));
                return matches(alias + "." + column, restriction.value(), parameters);
            }
            String ownerColumn = entity.ownerColumns().get(restricted.name());
            if (ownerColumn == null) {
                return null;
            }
            usages.add(new ColumnUsage(restricted.table(), column, ColumnUsage.Kind.FILTER));
            usages.add(new ColumnUsage(entity.table(), ownerColumn, ColumnUsage.Kind.FILTER));
            return alias + "." + ownerColumn + " IN (SELECT o." + restricted.idColumns().getFirst() + " FROM "
                    + restricted.table() + " o WHERE " + matches("o." + column, restriction.value(), parameters) + ")";
        }

        private static String matches(String expression, Object value, List<Object> parameters) {
            if (value instanceof Collection<?> values) {
                if (values.isEmpty()) {
                    return "1 = 0";
                }
                parameters.addAll(values);
                return expression + " IN (" + String.join(", ", Collections.nCopies(values.size(), "?")) + ")";
            }
            parameters.add(value);
            return expression + " = ?";
        }
    }

    // ---- value handling --------------------------------------------------------------------

    private static void requireType(Measure measure, FilterOperator operator, FieldType... allowed) {
        if (!List.of(allowed).contains(measure.type())) {
            throw new InvalidReportException("Operator " + operator + " cannot be used on " + measure.type()
                    + " value '" + measure.label() + "'");
        }
    }

    private static Object requireValue(Measure measure, Object value) {
        if (value == null) {
            throw new InvalidReportException("Condition on '" + measure.label() + "' needs a value");
        }
        return value;
    }

    private static List<?> listValue(Measure measure, FilterOperator operator, Object value) {
        if (!(value instanceof List<?> values)) {
            throw new InvalidReportException(operator + " on '" + measure.label() + "' needs a list of values");
        }
        return values;
    }

    private static long dayCount(FilterOperator operator, Object value) {
        if (value == null) {
            throw new InvalidReportException(operator + " needs a number of days");
        }
        try {
            long days = value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
            if (days < 0) {
                throw new InvalidReportException(operator + " needs a non-negative number of days");
            }
            return days;
        } catch (NumberFormatException e) {
            throw new InvalidReportException(operator + " needs a whole number of days, got: " + value);
        }
    }

    /** Converts a JSON value to the measure's type so the database compares like with like. */
    private static Object coerce(Measure measure, Object value) {
        requireValue(measure, value);
        if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
            throw new InvalidReportException("Condition on '" + measure.label() + "' needs a single value");
        }
        String text = value.toString();
        try {
            return switch (measure.type()) {
                case TEXT -> text;
                case NUMBER -> value instanceof Number ? value : new BigDecimal(text);
                case DATE -> LocalDate.parse(text);
                case DATE_TIME -> LocalDateTime.parse(text);
                case BOOLEAN -> value instanceof Boolean ? value : parseBoolean(text);
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new InvalidReportException("Value '" + text + "' is not a valid " + measure.type()
                    + " for '" + measure.label() + "'");
        }
    }

    private static Boolean parseBoolean(String text) {
        if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
            return Boolean.valueOf(text);
        }
        throw new NumberFormatException(text);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // ---- small value types -----------------------------------------------------------------

    /** One aliased occurrence of an entity in a query; the root has an empty path. */
    private static final class JoinNode {
        final String path;
        final ReportableEntity entity;
        final String alias;
        final JoinNode parent;
        final Navigation navigation;
        /** Label of a child-scope root, e.g. "Personnel / Training Records"; null = the entity's label. */
        final String rootLabel;
        final List<String> onConditions = new ArrayList<>();
        final List<Object> onParameters = new ArrayList<>();

        JoinNode(String path, ReportableEntity entity, String alias, JoinNode parent, Navigation navigation, String rootLabel) {
            this.path = path;
            this.entity = entity;
            this.alias = alias;
            this.parent = parent;
            this.navigation = navigation;
            this.rootLabel = rootLabel;
        }

        int depth() {
            return parent == null ? 0 : parent.depth() + 1;
        }

        boolean isAncestorOrSelfOf(JoinNode other) {
            for (JoinNode node = other; node != null; node = node.parent) {
                if (node == this) {
                    return true;
                }
            }
            return false;
        }

        String label() {
            if (parent == null) {
                return rootLabel != null ? rootLabel : entity.label();
            }
            return parent.label() + " / " + navigation.label();
        }
    }

    private record FieldRef(JoinNode node, CatalogField field, String path) {

        String sql() {
            return node.alias + "." + field.column();
        }

        String label() {
            return node.label() + " / " + field.label();
        }
    }

    /**
     * A plain or aggregated field (or COUNT(*), or a per-root subquery) as it appears in SELECT, WHERE,
     * HAVING or ORDER BY.
     *
     * @param listLimit max elements of a LIST (the SQL fetches one more to detect truncation), else null
     * @param keys      field paths of a LIST of objects, else null
     */
    private record Measure(FieldRef ref, Aggregate aggregate, String sql, FieldType type, String label,
                           Integer listLimit, List<String> keys) {

        static Measure of(FieldRef ref, Aggregate aggregate, String sql, FieldType type, String label) {
            return new Measure(ref, aggregate, sql, type, label, null, null);
        }

        Measure withListLimit(int limit) {
            return new Measure(ref, aggregate, sql, type, label, limit, keys);
        }

        boolean aggregated() {
            return aggregate != null;
        }
    }
}
