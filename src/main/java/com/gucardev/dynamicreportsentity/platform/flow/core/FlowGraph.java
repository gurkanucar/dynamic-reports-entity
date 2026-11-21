package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.core.FlowException.FieldProblem;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * How a flow's steps follow each other.
 * <ul>
 *   <li><b>Sequential</b> (no edges): in list order. A {@code false} condition or an approval that was not given ends
 *       the run.</li>
 *   <li><b>Graph</b>: one start step (no incoming edge); after a step, the edge whose {@code when} equals the step's
 *       outcome is taken, else the edge without {@code when}; no edge = the run ends. The graph has no cycles and at
 *       most one edge per step and outcome, so exactly one path is active at a time (branches may join again).</li>
 * </ul>
 */
final class FlowGraph {

    private final FlowDefinition definition;
    private final Map<String, Integer> index = new LinkedHashMap<>();
    private final Map<String, List<FlowEdge>> outgoing = new HashMap<>();
    private final Map<String, List<FlowEdge>> incoming = new HashMap<>();

    FlowGraph(FlowDefinition definition) {
        this.definition = definition;
        for (int i = 0; i < definition.steps().size(); i++) {
            index.putIfAbsent(definition.steps().get(i).key(), i);
        }
        for (FlowEdge edge : definition.edges()) {
            outgoing.computeIfAbsent(edge.from(), key -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.to(), key -> new ArrayList<>()).add(edge);
        }
    }

    boolean isSequential() {
        return definition.edges().isEmpty();
    }

    FlowStep step(String key) {
        return definition.steps().get(index.get(key));
    }

    int indexOf(String key) {
        return index.get(key);
    }

    /** The step a run begins with. */
    FlowStep start() {
        if (isSequential()) {
            return definition.steps().getFirst();
        }
        return definition.steps().stream().filter(step -> !incoming.containsKey(step.key())).findFirst()
                .orElseThrow(() -> new IllegalStateException("The flow has no start step"));
    }

    /** The step after {@code key} for its {@code outcome}; empty = the run ends here. */
    Optional<FlowStep> next(String key, String outcome) {
        if (isSequential()) {
            if (outcome != null && !outcome.equals("true") && !outcome.equals("approved")) {
                return Optional.empty();   // a guard said no
            }
            int next = indexOf(key) + 1;
            return next < definition.steps().size() ? Optional.of(definition.steps().get(next)) : Optional.empty();
        }
        List<FlowEdge> edges = outgoing.getOrDefault(key, List.of());
        return edges.stream().filter(edge -> outcome != null && outcome.equals(edge.when())).findFirst()
                .or(() -> edges.stream().filter(edge -> edge.when() == null).findFirst())
                .map(edge -> step(edge.to()));
    }

    /** Steps that always or possibly ran before {@code key} (its ancestors). */
    Set<String> before(String key) {
        Set<String> result = new HashSet<>();
        if (isSequential()) {
            for (int i = 0; i < indexOf(key); i++) {
                result.add(definition.steps().get(i).key());
            }
            return result;
        }
        Deque<String> queue = new ArrayDeque<>(List.of(key));
        while (!queue.isEmpty()) {
            for (FlowEdge edge : incoming.getOrDefault(queue.pop(), List.of())) {
                if (result.add(edge.from())) {
                    queue.push(edge.from());
                }
            }
        }
        return result;
    }

    /** Structural problems: unknown or duplicate edges, outcomes a step does not have, cycles, unreachable steps. */
    List<FieldProblem> problems() {
        List<FieldProblem> problems = new ArrayList<>();
        if (isSequential()) {
            return problems;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < definition.edges().size(); i++) {
            FlowEdge edge = definition.edges().get(i);
            String path = "edges[" + i + "]";
            if (!index.containsKey(edge.from()) || !index.containsKey(edge.to())) {
                problems.add(new FieldProblem(path, "EDGE_UNKNOWN_STEP",
                        "The edge connects a step that does not exist: " + edge.from() + " → " + edge.to() + "."));
                continue;
            }
            if (edge.from().equals(edge.to())) {
                problems.add(new FieldProblem(path, "CYCLE", "A step cannot lead to itself."));
            }
            if (!seen.add(edge.from() + "|" + edge.when())) {
                problems.add(new FieldProblem(path, "DUPLICATE_EDGE", "Step '" + edge.from() + "' already has an edge"
                        + (edge.when() == null ? "" : " for '" + edge.when() + "'") + "; only one path can be taken."));
            }
            Set<String> outcomes = step(edge.from()).outcomes();
            if (edge.when() != null && !outcomes.contains(edge.when())) {
                problems.add(new FieldProblem(path + ".when", "INVALID_EDGE_OUTCOME", outcomes.isEmpty()
                        ? "Step '" + edge.from() + "' has a single way out; leave 'when' empty."
                        : "Step '" + edge.from() + "' can only branch on " + String.join(", ", outcomes) + "."));
            }
        }
        if (!problems.isEmpty()) {
            return problems;
        }
        List<String> starts = definition.steps().stream().map(FlowStep::key)
                .filter(key -> !incoming.containsKey(key)).toList();
        if (starts.size() != 1) {
            problems.add(new FieldProblem("edges", starts.isEmpty() ? "NO_START_STEP" : "MULTIPLE_START_STEPS",
                    starts.isEmpty() ? "Every step has an incoming edge; one step must start the flow."
                            : "Only one step may be without incoming edges, found: " + String.join(", ", starts) + "."));
            return problems;
        }
        if (hasCycle()) {
            problems.add(new FieldProblem("edges", "CYCLE", "The steps form a loop; a flow must always move forward."));
            return problems;
        }
        Set<String> reachable = reachableFrom(starts.getFirst());
        for (int i = 0; i < definition.steps().size(); i++) {
            String key = definition.steps().get(i).key();
            if (!reachable.contains(key)) {
                problems.add(new FieldProblem("steps[" + i + "]", "UNREACHABLE_STEP",
                        "Step '" + key + "' can never be reached from '" + starts.getFirst() + "'."));
            }
        }
        return problems;
    }

    private Set<String> reachableFrom(String start) {
        Set<String> reached = new HashSet<>(List.of(start));
        Deque<String> queue = new ArrayDeque<>(List.of(start));
        while (!queue.isEmpty()) {
            for (FlowEdge edge : outgoing.getOrDefault(queue.pop(), List.of())) {
                if (reached.add(edge.to())) {
                    queue.push(edge.to());
                }
            }
        }
        return reached;
    }

    private boolean hasCycle() {
        Map<String, Integer> state = new HashMap<>();   // 1 = on the current path, 2 = done
        for (String key : index.keySet()) {
            if (visit(key, state)) {
                return true;
            }
        }
        return false;
    }

    private boolean visit(String key, Map<String, Integer> state) {
        Integer current = state.get(key);
        if (current != null) {
            return current == 1;
        }
        state.put(key, 1);
        for (FlowEdge edge : outgoing.getOrDefault(key, List.of())) {
            if (visit(edge.to(), state)) {
                return true;
            }
        }
        state.put(key, 2);
        return false;
    }
}
