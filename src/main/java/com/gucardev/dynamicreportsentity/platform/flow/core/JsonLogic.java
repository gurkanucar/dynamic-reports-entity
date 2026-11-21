package com.gucardev.dynamicreportsentity.platform.flow.core;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A small, dependency-free evaluator for <a href="https://jsonlogic.com">JSON Logic</a>, the expression language of
 * flow conditions and filters. Only the operators listed in {@link #OPERATORS} exist: no code, no reflection, no I/O,
 * and evaluation depth is bounded, so a rule written by a business user cannot do anything but compute a value.
 * <p>
 * Rules and data are plain JSON values (maps, lists, strings, numbers, booleans, null). Examples:
 * <pre>{@code
 * {">": [{"var": "steps.dataset.rowCount"}, 0]}
 * {"and": [{"==": [{"var": "trigger.status"}, "APPROVED"]}, {"<": [{"var": "item.amount"}, 1000]}]}
 * {"in": [{"var": "trigger.country"}, ["TR", "DE"]]}
 * }</pre>
 */
public final class JsonLogic {

    /** Operators that can be used; anything else is rejected when the flow is saved. */
    public static final Set<String> OPERATORS = Set.of(
            "var", "missing", "missing_some", "if", "?:",
            "==", "===", "!=", "!==", "!", "!!", "or", "and",
            ">", ">=", "<", "<=", "max", "min", "+", "-", "*", "/", "%",
            "in", "cat", "substr", "merge", "map", "filter", "reduce", "all", "some", "none");

    private static final int MAX_DEPTH = 64;

    /** The rule is not valid JSON Logic (unknown operator, wrong shape). */
    public static class InvalidRuleException extends RuntimeException {
        public InvalidRuleException(String message) {
            super(message);
        }
    }

    private JsonLogic() {
    }

    /** Checks the rule's shape and operators without evaluating it. */
    public static void check(Object rule) {
        check(rule, 0);
    }

    private static void check(Object rule, int depth) {
        if (depth > MAX_DEPTH) {
            throw new InvalidRuleException("The rule is nested too deeply");
        }
        if (rule instanceof Map<?, ?> map) {
            if (map.size() != 1) {
                throw new InvalidRuleException("An operation must have exactly one operator, found: " + map.keySet());
            }
            Map.Entry<?, ?> operation = map.entrySet().iterator().next();
            if (!(operation.getKey() instanceof String operator) || !OPERATORS.contains(operator)) {
                throw new InvalidRuleException("Unknown operator '" + operation.getKey() + "'");
            }
            check(operation.getValue(), depth + 1);
        } else if (rule instanceof List<?> list) {
            for (Object item : list) {
                check(item, depth + 1);
            }
        }
    }

    public static Object apply(Object rule, Object data) {
        return apply(rule, data, 0);
    }

    /** Evaluates the rule and applies JSON Logic truthiness. */
    public static boolean test(Object rule, Object data) {
        return truthy(apply(rule, data));
    }

    @SuppressWarnings("unchecked")
    private static Object apply(Object rule, Object data, int depth) {
        if (depth > MAX_DEPTH) {
            throw new InvalidRuleException("The rule is nested too deeply");
        }
        if (rule instanceof List<?> list) {
            List<Object> values = new ArrayList<>(list.size());
            for (Object item : list) {
                values.add(apply(item, data, depth + 1));
            }
            return values;
        }
        if (!(rule instanceof Map<?, ?> map) || map.size() != 1) {
            return rule;   // a literal
        }
        Map.Entry<String, Object> operation = ((Map<String, Object>) map).entrySet().iterator().next();
        String operator = operation.getKey();
        List<Object> args = operation.getValue() instanceof List<?> list ? (List<Object>) list
                : new ArrayList<>(java.util.Collections.singletonList(operation.getValue()));
        int next = depth + 1;

        // operators that decide themselves which arguments to evaluate
        switch (operator) {
            case "if", "?:" -> {
                int i = 0;
                for (; i + 1 < args.size(); i += 2) {
                    if (truthy(apply(args.get(i), data, next))) {
                        return apply(args.get(i + 1), data, next);
                    }
                }
                return i < args.size() ? apply(args.get(i), data, next) : null;
            }
            case "or" -> {
                Object last = null;
                for (Object arg : args) {
                    last = apply(arg, data, next);
                    if (truthy(last)) {
                        return last;
                    }
                }
                return last;
            }
            case "and" -> {
                Object last = null;
                for (Object arg : args) {
                    last = apply(arg, data, next);
                    if (!truthy(last)) {
                        return last;
                    }
                }
                return last;
            }
            case "map", "filter", "all", "some", "none", "reduce" -> {
                return iterate(operator, args, data, next);
            }
            default -> {
                // everything else works on evaluated arguments
            }
        }
        List<Object> values = new ArrayList<>(args.size());
        for (Object arg : args) {
            values.add(apply(arg, data, next));
        }
        return switch (operator) {
            case "var" -> variable(values, data);
            case "missing" -> missing(values, data);
            case "missing_some" -> missingSome(values, data);
            case "==" -> looseEquals(arg(values, 0), arg(values, 1));
            case "===" -> strictEquals(arg(values, 0), arg(values, 1));
            case "!=" -> !looseEquals(arg(values, 0), arg(values, 1));
            case "!==" -> !strictEquals(arg(values, 0), arg(values, 1));
            case "!" -> !truthy(arg(values, 0));
            case "!!" -> truthy(arg(values, 0));
            case ">" -> compare(values, ">");
            case ">=" -> compare(values, ">=");
            case "<" -> compare(values, "<");
            case "<=" -> compare(values, "<=");
            case "max" -> values.isEmpty() ? null : values.stream().map(JsonLogic::number).max(BigDecimal::compareTo)
                    .map(JsonLogic::plain).orElse(null);
            case "min" -> values.isEmpty() ? null : values.stream().map(JsonLogic::number).min(BigDecimal::compareTo)
                    .map(JsonLogic::plain).orElse(null);
            case "+" -> plain(values.stream().map(JsonLogic::number).reduce(BigDecimal.ZERO, BigDecimal::add));
            case "*" -> plain(values.stream().map(JsonLogic::number).reduce(BigDecimal.ONE, BigDecimal::multiply));
            case "-" -> values.size() == 1 ? plain(number(values.getFirst()).negate())
                    : plain(number(arg(values, 0)).subtract(number(arg(values, 1))));
            case "/" -> plain(number(arg(values, 0)).divide(number(arg(values, 1)), MathContext.DECIMAL64));
            case "%" -> plain(number(arg(values, 0)).remainder(number(arg(values, 1))));
            case "in" -> contains(arg(values, 1), arg(values, 0));
            case "cat" -> String.join("", values.stream().map(JsonLogic::text).toList());
            case "substr" -> substring(values);
            case "merge" -> merge(values);
            default -> throw new InvalidRuleException("Unknown operator '" + operator + "'");
        };
    }

    // ---- data access ----------------------------------------------------------------------------------

    private static Object variable(List<Object> values, Object data) {
        Object path = arg(values, 0);
        Object fallback = arg(values, 1);
        if (path == null || "".equals(path)) {
            return data;
        }
        Object value = lookup(data, text(path));
        return value == null ? fallback : value;
    }

    /** Dotted path lookup through maps and lists (numeric segments index lists). */
    static Object lookup(Object data, String path) {
        Object current = data;
        for (String segment : path.split("\\.")) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(segment);
            } else if (current instanceof List<?> list && segment.matches("\\d+")) {
                int index = Integer.parseInt(segment);
                current = index < list.size() ? list.get(index) : null;
            } else {
                return null;
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static List<Object> missing(List<Object> values, Object data) {
        List<Object> keys = values.size() == 1 && values.getFirst() instanceof List<?> list
                ? new ArrayList<>(list) : values;
        List<Object> missing = new ArrayList<>();
        for (Object key : keys) {
            Object value = lookup(data, text(key));
            if (value == null || "".equals(value)) {
                missing.add(key);
            }
        }
        return missing;
    }

    private static List<Object> missingSome(List<Object> values, Object data) {
        int needed = number(arg(values, 0)).intValue();
        List<Object> keys = arg(values, 1) instanceof List<?> list ? new ArrayList<>(list) : List.of();
        List<Object> missing = missing(List.of(keys), data);
        return keys.size() - missing.size() >= needed ? List.of() : missing;
    }

    // ---- arrays ---------------------------------------------------------------------------------------

    private static Object iterate(String operator, List<Object> args, Object data, int depth) {
        Object source = apply(arg(args, 0), data, depth);
        List<?> items = source instanceof List<?> list ? list : List.of();
        Object rule = arg(args, 1);
        switch (operator) {
            case "map" -> {
                List<Object> result = new ArrayList<>(items.size());
                for (Object item : items) {
                    result.add(apply(rule, item, depth));
                }
                return result;
            }
            case "filter" -> {
                List<Object> result = new ArrayList<>();
                for (Object item : items) {
                    if (truthy(apply(rule, item, depth))) {
                        result.add(item);
                    }
                }
                return result;
            }
            case "all" -> {
                if (items.isEmpty()) {
                    return false;
                }
                for (Object item : items) {
                    if (!truthy(apply(rule, item, depth))) {
                        return false;
                    }
                }
                return true;
            }
            case "some" -> {
                for (Object item : items) {
                    if (truthy(apply(rule, item, depth))) {
                        return true;
                    }
                }
                return false;
            }
            case "none" -> {
                for (Object item : items) {
                    if (truthy(apply(rule, item, depth))) {
                        return false;
                    }
                }
                return true;
            }
            default -> {   // reduce
                Object accumulator = apply(arg(args, 2), data, depth);
                for (Object item : items) {
                    accumulator = apply(rule, Map.of("current", item == null ? "" : item,
                            "accumulator", accumulator == null ? "" : accumulator), depth);
                }
                return accumulator;
            }
        }
    }

    private static Object contains(Object haystack, Object needle) {
        if (haystack instanceof String text) {
            return needle != null && text.contains(text(needle));
        }
        if (haystack instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> looseEquals(item, needle));
        }
        return false;
    }

    private static List<Object> merge(List<Object> values) {
        List<Object> result = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof Collection<?> collection) {
                result.addAll(collection);
            } else {
                result.add(value);
            }
        }
        return result;
    }

    private static String substring(List<Object> values) {
        String text = text(arg(values, 0));
        int start = number(arg(values, 1)).intValue();
        start = start < 0 ? Math.max(0, text.length() + start) : Math.min(start, text.length());
        if (values.size() < 3) {
            return text.substring(start);
        }
        int length = number(arg(values, 2)).intValue();
        int end = length < 0 ? Math.max(start, text.length() + length) : Math.min(text.length(), start + length);
        return text.substring(start, end);
    }

    // ---- comparison and conversion ----------------------------------------------------------------------

    private static boolean compare(List<Object> values, String operator) {
        if (values.size() == 3 && (operator.equals("<") || operator.equals("<="))) {   // between
            return compareTwo(arg(values, 0), arg(values, 1), operator)
                    && compareTwo(arg(values, 1), arg(values, 2), operator);
        }
        return compareTwo(arg(values, 0), arg(values, 1), operator);
    }

    private static boolean compareTwo(Object left, Object right, String operator) {
        int order;
        if (left instanceof String a && right instanceof String b) {
            order = a.compareTo(b);   // also ISO dates: "2026-01-31" < "2026-02-01"
        } else {
            BigDecimal a = numberOrNull(left);
            BigDecimal b = numberOrNull(right);
            if (a == null || b == null) {
                return false;
            }
            order = a.compareTo(b);
        }
        return switch (operator) {
            case ">" -> order > 0;
            case ">=" -> order >= 0;
            case "<" -> order < 0;
            default -> order <= 0;
        };
    }

    static boolean looseEquals(Object left, Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left instanceof Number || right instanceof Number) {
            BigDecimal a = numberOrNull(left);
            BigDecimal b = numberOrNull(right);
            return a != null && b != null && a.compareTo(b) == 0;
        }
        if (left instanceof Boolean || right instanceof Boolean) {
            return truthy(left) == truthy(right) && !(left instanceof String s && s.isEmpty() && truthy(right));
        }
        return Objects.equals(text(left), text(right));
    }

    private static boolean strictEquals(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            return number(left).compareTo(number(right)) == 0;
        }
        return Objects.equals(left, right);
    }

    /** JSON Logic truthiness: false, null, 0, "" and [] are false. */
    public static boolean truthy(Object value) {
        return switch (value) {
            case null -> false;
            case Boolean bool -> bool;
            case Number number -> number(number).signum() != 0;
            case String text -> !text.isEmpty();
            case Collection<?> collection -> !collection.isEmpty();
            default -> true;
        };
    }

    private static BigDecimal number(Object value) {
        BigDecimal number = numberOrNull(value);
        return number == null ? BigDecimal.ZERO : number;
    }

    private static BigDecimal numberOrNull(Object value) {
        return switch (value) {
            case null -> null;
            case BigDecimal decimal -> decimal;
            case Number number -> new BigDecimal(number.toString());
            case Boolean bool -> bool ? BigDecimal.ONE : BigDecimal.ZERO;
            case String text -> {
                try {
                    yield new BigDecimal(text.strip());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    /** Whole numbers come back as longs, others as doubles, like JSON would carry them. */
    private static Object plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() <= 0 && stripped.abs().compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0) {
            return stripped.longValueExact();
        }
        return value.doubleValue();
    }

    private static String text(Object value) {
        return value == null ? "" : value instanceof BigDecimal decimal ? decimal.toPlainString() : value.toString();
    }

    private static Object arg(List<Object> values, int index) {
        return index < values.size() ? values.get(index) : null;
    }
}
