package com.gucardev.dynamicreportsentity.reporting.query;

import com.gucardev.dynamicreportsentity.reporting.ReportAccessDeniedException;
import com.gucardev.dynamicreportsentity.reporting.ReportRowPolicy;
import com.gucardev.dynamicreportsentity.reporting.ReportViewer;
import com.gucardev.dynamicreportsentity.reporting.RowRestriction;
import com.gucardev.dynamicreportsentity.reporting.catalog.CatalogField;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.joining;

/**
 * Turns a {@link ReportDefinition} into SQL.
 * <ul>
 *   <li><b>Joins</b> come from the paths: every distinct path prefix ({@code manager},
 *       {@code manager.department}) becomes one aliased LEFT JOIN of the named navigation. The
 *       user never writes a JOIN and the developer never writes per-report code.</li>
 *   <li><b>Grouping</b>: as soon as a column is aggregated, all non-aggregated columns form the GROUP BY.</li>
 *   <li><b>Fan-out protection</b>: combining two independent one-to-many navigations, or summing a
 *       value that a one-to-many join duplicates, is rejected instead of returning wrong numbers.</li>
 *   <li><b>Access control</b> happens here, not in the UI: every field used anywhere (column, where,
 *       having, sort) is permission-checked, and row restrictions are always appended.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ReportQueryBuilder {

    static final int MAX_PATH_DEPTH = 4;
    static final int MAX_CONDITIONS = 50;
    static final int MAX_NESTING = 5;
    static final int MAX_LIST_VALUES = 1000;

    private final ReportCatalog catalog;
    private final Clock clock;
    private final List<ReportRowPolicy> rowPolicies;

    public ReportQuery build(ReportDefinition definition, ReportViewer viewer) {
        ReportableEntity root = catalog.entity(definition.root())
                .orElseThrow(() -> new InvalidReportException("Unknown entity: " + definition.root()));
        return new Assembly(root, viewer).assemble(definition);
    }

    /** State of one build: the join tree grows while paths are resolved. */
    private final class Assembly {

        private final ReportViewer viewer;
        private final JoinNode root;
        private final Map<String, JoinNode> nodes = new LinkedHashMap<>();
        private final List<Measure> measures = new ArrayList<>();
        private int conditionCount;

        Assembly(ReportableEntity rootEntity, ReportViewer viewer) {
            this.viewer = viewer;
            this.root = new JoinNode("", rootEntity, "t0", null, null);
            nodes.put("", root);
        }

        ReportQuery assemble(ReportDefinition definition) {
            List<Measure> columns = definition.columns().stream()
                    .map(column -> measure(column.field(), column.dateGroup(), column.aggregate()))
                    .toList();
            boolean grouped = columns.stream().anyMatch(Measure::aggregated);
            List<String> groupBy = grouped
                    ? columns.stream().filter(column -> !column.aggregated()).map(Measure::sql).distinct().toList()
                    : List.of();

            List<String> where = new ArrayList<>();
            List<Object> whereParameters = new ArrayList<>();
            if (definition.where() != null) {
                where.add(render(definition.where(), false, whereParameters, 1));
            }

            String having = null;
            List<Object> havingParameters = new ArrayList<>();
            if (definition.having() != null) {
                if (!grouped) {
                    throw new InvalidReportException("'having' needs at least one aggregated column");
                }
                having = render(definition.having(), true, havingParameters, 1);
            }

            List<String> orderBy = new ArrayList<>();
            Set<String> ordered = new HashSet<>();
            for (ReportSort sort : definition.sort()) {
                Measure measure = measure(sort.field(), sort.dateGroup(), sort.aggregate());
                if (grouped && !measure.aggregated() && !groupBy.contains(measure.sql())) {
                    throw new InvalidReportException("Cannot sort by '" + sort.field()
                            + "': in a grouped report only grouped columns or aggregates can be sorted");
                }
                if (!grouped && measure.aggregated()) {
                    throw new InvalidReportException("Sorting by an aggregate needs aggregated columns");
                }
                if (ordered.add(measure.sql())) {
                    orderBy.add(measure.sql() + " " + sort.direction());
                }
            }

            checkFanOut();
            applyRowRestrictions(where, whereParameters);

            // Paging needs a stable order: user's sort, then whatever makes a row unique.
            List<String> tieBreakers = grouped ? groupBy : nodes.values().stream()
                    .flatMap(node -> node.entity.idColumns().stream().map(column -> node.alias + "." + column))
                    .toList();
            tieBreakers.stream().filter(ordered::add).forEach(orderBy::add);

            List<Object> parameters = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT ")
                    .append(columns.stream().map(Measure::sql).collect(joining(", ")))
                    .append("\nFROM ").append(root.entity.table()).append(' ').append(root.alias);
            for (JoinNode node : nodes.values()) {
                if (node == root) {
                    continue;
                }
                sql.append("\nLEFT JOIN ").append(node.entity.table()).append(' ').append(node.alias)
                        .append(" ON ").append(node.alias).append('.').append(node.navigation.joinColumn())
                        .append(" = ").append(node.parent.alias).append('.').append(node.navigation.parentColumn());
                node.onConditions.forEach(condition -> sql.append(" AND ").append(condition));
                parameters.addAll(node.onParameters);
            }
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
            if (!orderBy.isEmpty()) {
                sql.append("\nORDER BY ").append(String.join(", ", orderBy));
            }

            List<ResultColumn> resultColumns = new ArrayList<>();
            for (int i = 0; i < columns.size(); i++) {
                ReportColumn requested = definition.columns().get(i);
                Measure measure = columns.get(i);
                String label = requested.label() == null || requested.label().isBlank() ? measure.label() : requested.label();
                resultColumns.add(new ResultColumn(requested.field(), requested.aggregate(), requested.dateGroup(),
                        label, measure.type()));
            }
            return new ReportQuery(sql.toString(), Collections.unmodifiableList(parameters), List.copyOf(resultColumns));
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
                throw new InvalidReportException("Unknown relation '" + name + "' on " + parent.entity.label()
                        + " (in '" + path + "')");
            }
            ReportableEntity target = catalog.entity(navigation.targetEntity()).orElseThrow();
            JoinNode node = new JoinNode(key, target, "t" + nodes.size(), parent, navigation);
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
                return track(new Measure(null, aggregate, "COUNT(*)", FieldType.NUMBER, "Count of " + root.entity.label()));
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
                return track(new Measure(ref, null, expression, type, label));
            }
            if ((aggregate == Aggregate.SUM || aggregate == Aggregate.AVG) && type != FieldType.NUMBER) {
                throw new InvalidReportException(aggregate + " needs a numeric field, '" + path + "' is " + type);
            }
            String sql = aggregate == Aggregate.COUNT_DISTINCT
                    ? "COUNT(DISTINCT " + expression + ")"
                    : aggregate.name() + "(" + expression + ")";
            FieldType resultType = aggregate == Aggregate.MIN || aggregate == Aggregate.MAX ? type : FieldType.NUMBER;
            return track(new Measure(ref, aggregate, sql, resultType, aggregate.label() + " of " + label));
        }

        private Measure track(Measure measure) {
            measures.add(measure);
            return measure;
        }

        // ---- conditions ----------------------------------------------------------------------

        private String render(ReportCondition condition, boolean having, List<Object> parameters, int depth) {
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
                    parts.add(render(child, having, parameters, depth + 1));
                }
                return "(" + String.join(joiner, parts) + ")";
            }
            if (++conditionCount > MAX_CONDITIONS) {
                throw new InvalidReportException("Too many conditions (max " + MAX_CONDITIONS + ")");
            }
            if (condition.operator() == null) {
                throw new InvalidReportException("Condition on '" + condition.field() + "' needs an operator");
            }
            if (having && condition.aggregate() == null) {
                throw new InvalidReportException("Conditions in 'having' must use an aggregate (field '" + condition.field() + "')");
            }
            if (!having && condition.aggregate() != null) {
                throw new InvalidReportException("Aggregates can only be filtered in 'having'");
            }
            return predicate(measure(condition.field(), condition.dateGroup(), condition.aggregate()),
                    condition.operator(), condition.value(), parameters);
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
         */
        private void checkFanOut() {
            List<JoinNode> toMany = nodes.values().stream()
                    .filter(node -> node.navigation != null && node.navigation.toMany())
                    .toList();
            for (int i = 0; i < toMany.size(); i++) {
                for (int j = i + 1; j < toMany.size(); j++) {
                    JoinNode a = toMany.get(i);
                    JoinNode b = toMany.get(j);
                    if (!a.isAncestorOrSelfOf(b) && !b.isAncestorOrSelfOf(a)) {
                        throw new InvalidReportException("'" + a.path + "' and '" + b.path + "' are separate one-to-many"
                                + " relations; combining them would multiply rows. Split the report or start from another entity.");
                    }
                }
            }
            JoinNode grain = toMany.stream().max(Comparator.comparingInt(JoinNode::depth)).orElse(root);
            for (Measure measure : measures) {
                if (measure.ref() != null && measure.aggregated() && measure.aggregate().isSensitiveToDuplicates()
                        && !grain.isAncestorOrSelfOf(measure.ref().node())) {
                    throw new InvalidReportException(measure.aggregate() + " of '" + measure.ref().path()
                            + "' would be inflated by the one-to-many relation '" + grain.path
                            + "'. Use COUNT_DISTINCT, MIN or MAX, or start the report from " + grain.entity.label() + ".");
                }
            }
        }

        // ---- row-level security --------------------------------------------------------------

        private void applyRowRestrictions(List<String> where, List<Object> whereParameters) {
            for (ReportRowPolicy policy : rowPolicies) {
                for (RowRestriction restriction : policy.restrictionsFor(viewer)) {
                    ReportableEntity restricted = catalog.entity(restriction.entity()).orElseThrow(() ->
                            new IllegalStateException("Row restriction on unknown entity " + restriction.entity()));
                    String column = restricted.columns().get(restriction.field());
                    if (column == null) {
                        throw new IllegalStateException("Row restriction on unknown field "
                                + restriction.entity() + "." + restriction.field());
                    }
                    for (JoinNode node : nodes.values()) {
                        List<Object> conditionParameters = new ArrayList<>();
                        String condition;
                        if (node.entity.name().equals(restricted.name())) {
                            condition = matches(node.alias + "." + column, restriction.value(), conditionParameters);
                        } else if (node.entity.ownerColumns().containsKey(restricted.name())) {
                            condition = node.alias + "." + node.entity.ownerColumns().get(restricted.name())
                                    + " IN (SELECT o." + restricted.idColumns().getFirst() + " FROM " + restricted.table()
                                    + " o WHERE " + matches("o." + column, restriction.value(), conditionParameters) + ")";
                        } else {
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

    /** One aliased occurrence of an entity in the query; the root has an empty path. */
    private static final class JoinNode {
        final String path;
        final ReportableEntity entity;
        final String alias;
        final JoinNode parent;
        final Navigation navigation;
        final List<String> onConditions = new ArrayList<>();
        final List<Object> onParameters = new ArrayList<>();

        JoinNode(String path, ReportableEntity entity, String alias, JoinNode parent, Navigation navigation) {
            this.path = path;
            this.entity = entity;
            this.alias = alias;
            this.parent = parent;
            this.navigation = navigation;
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
            return parent == null ? entity.label() : parent.label() + " / " + navigation.label();
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

    /** A plain or aggregated field (or COUNT(*)) as it appears in SELECT, WHERE, HAVING or ORDER BY. */
    private record Measure(FieldRef ref, Aggregate aggregate, String sql, FieldType type, String label) {

        boolean aggregated() {
            return aggregate != null;
        }
    }
}
