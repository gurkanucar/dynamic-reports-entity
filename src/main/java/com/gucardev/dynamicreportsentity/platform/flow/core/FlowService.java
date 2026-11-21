package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowStep;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepCheck;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepType;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.Kind;
import com.gucardev.dynamicreportsentity.platform.notification.TemplateRenderer;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flow definitions. A user sees and changes their own flows; a REPORT_ADMIN sees and changes all of them.
 * Other users' flows answer 404, not 403, so their existence does not leak.
 * <p>
 * A flow is checked completely when saved: each step by its {@link StepType} (e.g. report definitions are built for
 * the editor and the owner), the graph by {@link FlowGraph}.
 */
@Service
public class FlowService {

    public static final String ADMIN_AUTHORITY = "REPORT_ADMIN";

    /** A flow as entered by the user. */
    public record FlowInput(String name, String description, List<FlowStep> steps, List<FlowEdge> edges,
                            Map<String, FlowDefinition.Position> layout) {

        public FlowInput(String name, String description, List<FlowStep> steps) {
            this(name, description, steps, List.of(), Map.of());
        }
    }

    private final FlowRepository flows;
    private final FlowScheduleRepository schedules;
    private final FlowTriggerRepository triggers;
    private final FlowRunRepository runs;
    private final StepTypes types;
    private final TemplateRenderer renderer;
    private final RunAsResolver runAs;
    private final Clock clock;

    FlowService(FlowRepository flows, FlowScheduleRepository schedules, FlowTriggerRepository triggers,
                FlowRunRepository runs, StepTypes types, TemplateRenderer renderer, RunAsResolver runAs, Clock clock) {
        this.flows = flows;
        this.schedules = schedules;
        this.triggers = triggers;
        this.runs = runs;
        this.types = types;
        this.renderer = renderer;
        this.runAs = runAs;
        this.clock = clock;
    }

    public Page<Flow> list(Actor viewer, int page, int size) {
        PageRequest request = PageRequest.of(page - 1, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        return viewer.hasAuthority(ADMIN_AUTHORITY) ? flows.findAll(request)
                : flows.findByOwner(viewer.username(), request);
    }

    public Flow get(long id, Actor viewer) {
        Flow flow = flows.findById(id).orElseThrow(() -> notFound(id));
        if (!canSee(flow, viewer)) {
            throw notFound(id);
        }
        return flow;
    }

    @Transactional
    public Flow create(FlowInput input, Actor editor) {
        FlowDefinition definition = check(input, editor, editor);
        Flow flow = new Flow(editor.username(), clock.instant());
        flow.define(input.name().strip(), input.description(), definition, types.references(definition),
                editor.username(), clock.instant());
        return flows.saveAndFlush(flow);
    }

    @Transactional
    public Flow update(long id, long expectedVersion, FlowInput input, Actor editor) {
        Flow flow = get(id, editor);
        if (flow.getVersion() != expectedVersion) {
            throw versionMismatch();
        }
        Actor owner = flow.getOwner().equals(editor.username()) ? editor
                : runAs.find(flow.getOwner()).orElse(null);
        FlowDefinition definition = check(input, editor, owner);
        flow.define(input.name().strip(), input.description(), definition, types.references(definition),
                editor.username(), clock.instant());
        try {
            return flows.saveAndFlush(flow);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw versionMismatch();
        }
    }

    /** Deletes the flow and its schedules; its finished runs stay as history. */
    @Transactional
    public void delete(long id, Actor editor) {
        Flow flow = get(id, editor);
        if (runs.existsByFlowIdAndStatusIn(id, RunStatus.ACTIVE)) {
            throw new FlowException(Kind.CONFLICT, "FLOW_HAS_ACTIVE_RUNS",
                    "The flow has queued or running runs; cancel them first.");
        }
        schedules.deleteByFlowId(id);
        triggers.deleteByFlowId(id);
        flows.delete(flow);
    }

    static boolean canSee(Flow flow, Actor viewer) {
        return flow.getOwner().equals(viewer.username()) || viewer.hasAuthority(ADMIN_AUTHORITY);
    }

    /**
     * Checks every step through its {@link StepType} and the graph, reporting every problem at once.
     *
     * @param owner the user the flow runs as (null if they no longer exist: only the editor is checked)
     */
    private FlowDefinition check(FlowInput input, Actor editor, Actor owner) {
        List<FieldProblem> problems = new ArrayList<>();
        List<FlowStep> steps = input.steps();
        FlowDefinition definition = new FlowDefinition(steps, input.edges(), input.layout());
        FlowGraph graph = new FlowGraph(definition);
        boolean uniqueKeys = steps.stream().map(FlowStep::key).distinct().count() == steps.size();
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < steps.size(); i++) {   // step by step, so problems come in the order of the steps
            FlowStep step = steps.get(i);
            if (!keys.add(step.key())) {
                problems.add(new FieldProblem("steps[" + i + "].key", "DUPLICATE_STEP_KEY",
                        "Another step already uses the key '" + step.key() + "'."));
            }
            Set<String> before = uniqueKeys ? graph.before(step.key()) : Set.of();
            types.of(step).validate(step, new Check("steps[" + i + "]", editor, owner, before, graph, problems));
        }
        if (uniqueKeys) {   // edges refer to keys: only meaningful when they are unique
            problems.addAll(graph.problems());
        }
        if (!problems.isEmpty()) {
            throw FlowException.invalid(problems);
        }
        return definition;
    }

    /** {@link StepCheck} for one step: fields become {@code steps[i].field}. */
    private final class Check implements StepCheck {

        private final String path;
        private final Actor editor;
        private final Actor owner;
        private final Set<String> before;
        private final FlowGraph graph;
        private final List<FieldProblem> problems;

        Check(String path, Actor editor, Actor owner, Set<String> before, FlowGraph graph, List<FieldProblem> problems) {
            this.path = path;
            this.editor = editor;
            this.owner = owner;
            this.before = before;
            this.graph = graph;
            this.problems = problems;
        }

        @Override
        public void problem(String field, String code, String message) {
            problems.add(new FieldProblem(field == null || field.isEmpty() ? path : path + "." + field, code, message));
        }

        @Override
        public void template(String field, String template) {
            try {
                renderer.check(template);
            } catch (TemplateRenderer.TemplateSyntaxException e) {
                problem(field, "TEMPLATE_SYNTAX_ERROR", e.getMessage());
            }
        }

        @Override
        public void expression(String field, Object rule) {
            try {
                JsonLogic.check(rule);
            } catch (JsonLogic.InvalidRuleException e) {
                problem(field, "INVALID_EXPRESSION", e.getMessage());
            }
        }

        @Override
        public Actor editor() {
            return editor;
        }

        @Override
        public Actor owner() {
            return owner;
        }

        @Override
        public Set<String> stepsBefore() {
            return before;
        }

        @Override
        public boolean producesFiles(String key) {
            return types.producesFiles(graph.step(key));
        }
    }

    private static FlowException notFound(long id) {
        return FlowException.notFound("FLOW_NOT_FOUND", "Flow " + id + " does not exist.");
    }

    private static FlowException versionMismatch() {
        return new FlowException(Kind.PRECONDITION_FAILED, "VERSION_MISMATCH",
                "The flow was changed by someone else; reload it and try again.");
    }
}
