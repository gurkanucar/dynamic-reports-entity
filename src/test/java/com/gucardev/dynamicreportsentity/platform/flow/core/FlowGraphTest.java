package com.gucardev.dynamicreportsentity.platform.flow.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlowGraphTest {

    private static FlowStep mail(String key) {
        return new MailStep(key, 1L, List.of("a@example.com"), null, null, null, null);
    }

    private static FlowStep condition(String key) {
        return new ConditionStep(key, Map.of("==", List.of(1, 1)), null, null);
    }

    private static FlowStep approval(String key) {
        return new ApprovalStep(key, 1L, "Approve?", null, null, null, null, null, null);
    }

    private static FlowGraph graph(List<FlowStep> steps, FlowEdge... edges) {
        return new FlowGraph(new FlowDefinition(steps, List.of(edges), Map.of()));
    }

    private static List<String> codes(FlowGraph graph) {
        return graph.problems().stream().map(FlowException.FieldProblem::code).toList();
    }

    @Test
    void withoutEdgesStepsRunInOrderAndGuardsStopTheRun() {
        FlowGraph sequential = new FlowGraph(new FlowDefinition(List.of(condition("check"), mail("a"), mail("b"))));
        assertThat(sequential.start().key()).isEqualTo("check");
        assertThat(sequential.next("check", "true")).map(FlowStep::key).contains("a");
        assertThat(sequential.next("check", "false")).isEmpty();
        assertThat(sequential.next("a", null)).map(FlowStep::key).contains("b");
        assertThat(sequential.next("b", null)).isEmpty();
        assertThat(sequential.before("b")).containsExactlyInAnyOrder("check", "a");
        assertThat(sequential.problems()).isEmpty();
    }

    @Test
    void edgesBranchOnOutcomesAndJoinAgain() {
        FlowGraph graph = graph(List.of(approval("ask"), mail("yes"), mail("no"), mail("done")),
                new FlowEdge("ask", "yes", "approved"),
                new FlowEdge("ask", "no", null),          // rejected and timeout
                new FlowEdge("yes", "done", null),
                new FlowEdge("no", "done", null));
        assertThat(graph.problems()).isEmpty();
        assertThat(graph.start().key()).isEqualTo("ask");
        assertThat(graph.next("ask", "approved")).map(FlowStep::key).contains("yes");
        assertThat(graph.next("ask", "rejected")).map(FlowStep::key).contains("no");
        assertThat(graph.next("ask", "timeout")).map(FlowStep::key).contains("no");
        assertThat(graph.next("done", null)).isEmpty();
        assertThat(graph.before("done")).containsExactlyInAnyOrder("ask", "yes", "no");
    }

    @Test
    void brokenGraphsAreRejected() {
        assertThat(codes(graph(List.of(mail("a"), mail("b")), new FlowEdge("a", "b", null),
                new FlowEdge("b", "a", null)))).containsExactly("NO_START_STEP");
        assertThat(codes(graph(List.of(mail("a"), mail("b"), mail("c")), new FlowEdge("b", "c", null),
                new FlowEdge("c", "b", null), new FlowEdge("a", "b", null)))).containsExactly("CYCLE");
        assertThat(codes(graph(List.of(mail("a"), mail("b"), mail("c")), new FlowEdge("a", "b", null))))
                .containsExactly("MULTIPLE_START_STEPS");
        assertThat(codes(graph(List.of(mail("a"), mail("b")), new FlowEdge("a", "x", null))))
                .containsExactly("EDGE_UNKNOWN_STEP");
        assertThat(codes(graph(List.of(mail("a"), mail("b"), mail("c")), new FlowEdge("a", "b", null),
                new FlowEdge("a", "c", null)))).contains("DUPLICATE_EDGE");
        assertThat(codes(graph(List.of(mail("a"), mail("b")), new FlowEdge("a", "b", "true"))))
                .containsExactly("INVALID_EDGE_OUTCOME");
        assertThat(codes(graph(List.of(condition("c"), mail("b")), new FlowEdge("c", "b", "maybe"))))
                .containsExactly("INVALID_EDGE_OUTCOME");
    }
}
