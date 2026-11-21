package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.core.FlowException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowException.Kind;
import com.gucardev.dynamicreportsentity.platform.flow.approval.ApprovalGroupRepository;
import com.gucardev.dynamicreportsentity.platform.flow.mail.MailTemplateRepository;
import com.gucardev.dynamicreportsentity.platform.flow.mail.TemplateRenderer;
import com.gucardev.dynamicreportsentity.platform.reporting.ReportAccessDeniedException;
import com.gucardev.dynamicreportsentity.platform.reporting.ReportViewer;
import com.gucardev.dynamicreportsentity.platform.reporting.query.InvalidReportException;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ReportExecutor;
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
 * A flow is checked completely when saved: report definitions are built (not run) for the editor and for the
 * owner, templates must exist, recipients must be allowed, attachments must name earlier report steps.
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
    private final MailTemplateRepository templates;
    private final ApprovalGroupRepository approvalGroups;
    private final TemplateRenderer renderer;
    private final ReportExecutor reports;
    private final RunAsResolver runAs;
    private final FlowProperties properties;
    private final Clock clock;

    FlowService(FlowRepository flows, FlowScheduleRepository schedules, FlowTriggerRepository triggers,
                FlowRunRepository runs,
                MailTemplateRepository templates, ApprovalGroupRepository approvalGroups,
                TemplateRenderer renderer, ReportExecutor reports, RunAsResolver runAs,
                FlowProperties properties, Clock clock) {
        this.flows = flows;
        this.schedules = schedules;
        this.triggers = triggers;
        this.runs = runs;
        this.templates = templates;
        this.approvalGroups = approvalGroups;
        this.renderer = renderer;
        this.reports = reports;
        this.runAs = runAs;
        this.properties = properties;
        this.clock = clock;
    }

    public Page<Flow> list(ReportViewer viewer, int page, int size) {
        PageRequest request = PageRequest.of(page - 1, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        return viewer.hasAuthority(ADMIN_AUTHORITY) ? flows.findAll(request)
                : flows.findByOwner(viewer.username(), request);
    }

    public Flow get(long id, ReportViewer viewer) {
        Flow flow = flows.findById(id).orElseThrow(() -> notFound(id));
        if (!canSee(flow, viewer)) {
            throw notFound(id);
        }
        return flow;
    }

    @Transactional
    public Flow create(FlowInput input, ReportViewer editor) {
        FlowDefinition definition = check(input, editor, editor);
        Flow flow = new Flow(editor.username(), clock.instant());
        flow.define(input.name().strip(), input.description(), definition, editor.username(), clock.instant());
        return flows.saveAndFlush(flow);
    }

    @Transactional
    public Flow update(long id, long expectedVersion, FlowInput input, ReportViewer editor) {
        Flow flow = get(id, editor);
        if (flow.getVersion() != expectedVersion) {
            throw versionMismatch();
        }
        ReportViewer owner = flow.getOwner().equals(editor.username()) ? editor
                : runAs.find(flow.getOwner()).orElse(null);
        FlowDefinition definition = check(input, editor, owner);
        flow.define(input.name().strip(), input.description(), definition, editor.username(), clock.instant());
        try {
            return flows.saveAndFlush(flow);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw versionMismatch();
        }
    }

    /** Deletes the flow and its schedules; its finished runs stay as history. */
    @Transactional
    public void delete(long id, ReportViewer editor) {
        Flow flow = get(id, editor);
        if (runs.existsByFlowIdAndStatusIn(id, RunStatus.ACTIVE)) {
            throw new FlowException(Kind.CONFLICT, "FLOW_HAS_ACTIVE_RUNS",
                    "The flow has queued or running runs; cancel them first.");
        }
        schedules.deleteByFlowId(id);
        triggers.deleteByFlowId(id);
        flows.delete(flow);
    }

    static boolean canSee(Flow flow, ReportViewer viewer) {
        return flow.getOwner().equals(viewer.username()) || viewer.hasAuthority(ADMIN_AUTHORITY);
    }

    /**
     * @param owner the user the flow runs as (null if they no longer exist: only the editor is checked)
     */
    private FlowDefinition check(FlowInput input, ReportViewer editor, ReportViewer owner) {
        List<FieldProblem> problems = new ArrayList<>();
        List<FlowStep> steps = input.steps();
        FlowDefinition definition = new FlowDefinition(steps, input.edges(), input.layout());
        FlowGraph graph = new FlowGraph(definition);
        Set<String> keys = new HashSet<>();
        boolean uniqueKeys = true;
        // every problem at once, so the user can fix them in one go
        for (int i = 0; i < steps.size(); i++) {
            FlowStep step = steps.get(i);
            String path = "steps[" + i + "]";
            if (!keys.add(step.key())) {
                uniqueKeys = false;
                problems.add(new FieldProblem(path + ".key", "DUPLICATE_STEP_KEY",
                        "Another step already uses the key '" + step.key() + "'."));
            }
            switch (step) {
                case ReportStep report -> checkReport(report, path, editor, owner, problems);
                case MailStep mail -> checkMail(mail, path, graph, problems);
                case ConditionStep condition -> checkExpression(condition.expression(), path + ".expression", problems);
                case FilterStep filter -> checkExpression(filter.expression(), path + ".expression", problems);
                case ApprovalStep approval -> checkApproval(approval, path, problems);
                case WebhookStep webhook -> checkWebhook(webhook, path, problems);
                case KafkaPublishStep kafka -> checkKafka(kafka, path, problems);
            }
        }
        if (uniqueKeys) {   // edges refer to keys: only meaningful when they are unique
            problems.addAll(graph.problems());
        }
        if (!problems.isEmpty()) {
            throw FlowException.invalid(problems);
        }
        return definition;
    }

    private void checkExpression(Object expression, String path, List<FieldProblem> problems) {
        try {
            JsonLogic.check(expression);
        } catch (JsonLogic.InvalidRuleException e) {
            problems.add(new FieldProblem(path, "INVALID_EXPRESSION", e.getMessage()));
        }
    }

    private void checkApproval(ApprovalStep step, String path, List<FieldProblem> problems) {
        if (!approvalGroups.existsById(step.groupId())) {
            problems.add(new FieldProblem(path + ".groupId", "APPROVAL_GROUP_NOT_FOUND",
                    "Approval group " + step.groupId() + " does not exist."));
        }
        checkTemplate(step.title(), path + ".title", problems);
        if (step.description() != null) {
            checkTemplate(step.description(), path + ".description", problems);
        }
    }

    private void checkWebhook(WebhookStep step, String path, List<FieldProblem> problems) {
        java.net.URI url;
        try {
            url = java.net.URI.create(step.url().strip());
        } catch (IllegalArgumentException e) {
            problems.add(new FieldProblem(path + ".url", "INVALID_URL", "Not a valid URL."));
            return;
        }
        if (!"http".equalsIgnoreCase(url.getScheme()) && !"https".equalsIgnoreCase(url.getScheme())) {
            problems.add(new FieldProblem(path + ".url", "INVALID_URL", "Only http and https URLs can be called."));
        } else if (!properties.webhook().allows(url.getHost())) {
            problems.add(new FieldProblem(path + ".url", "WEBHOOK_HOST_NOT_ALLOWED", "Calls to " + url.getHost()
                    + " are not allowed (flows.webhook.allowed-hosts)."));
        }
        if (step.body() != null) {
            checkTemplate(step.body(), path + ".body", problems);
        }
    }

    private void checkKafka(KafkaPublishStep step, String path, List<FieldProblem> problems) {
        if (!properties.kafka().enabled()) {
            problems.add(new FieldProblem(path, "KAFKA_DISABLED", "Kafka is not enabled (flows.kafka.enabled)."));
        } else if (!properties.kafka().allows(step.topic())) {
            problems.add(new FieldProblem(path + ".topic", "TOPIC_NOT_ALLOWED", "Publishing to " + step.topic()
                    + " is not allowed (flows.kafka.allowed-topics)."));
        }
        checkTemplate(step.payload(), path + ".payload", problems);
        if (step.messageKey() != null) {
            checkTemplate(step.messageKey(), path + ".messageKey", problems);
        }
    }

    private void checkTemplate(String template, String path, List<FieldProblem> problems) {
        try {
            renderer.check(template);
        } catch (TemplateRenderer.TemplateSyntaxException e) {
            problems.add(new FieldProblem(path, "TEMPLATE_SYNTAX_ERROR", e.getMessage()));
        }
    }

    private void checkReport(ReportStep step, String path, ReportViewer editor, ReportViewer owner,
                             List<FieldProblem> problems) {
        if (step.maxRows() != null && step.maxRows() > properties.reportMaxRows()) {
            problems.add(new FieldProblem(path + ".maxRows", "MAX_ROWS_TOO_HIGH",
                    "At most " + properties.reportMaxRows() + " rows are allowed."));
        }
        try {
            reports.prepare(step.definition(), editor);
            if (owner != null && owner != editor) {
                reports.prepare(step.definition(), owner);
            }
        } catch (InvalidReportException e) {
            problems.add(new FieldProblem(path + ".definition", "INVALID_REPORT_DEFINITION", e.getMessage()));
        } catch (ReportAccessDeniedException e) {
            throw new FlowException(Kind.FORBIDDEN, "REPORT_FIELD_FORBIDDEN",
                    "Step '" + step.key() + "': " + e.getMessage());
        }
    }

    private void checkMail(MailStep step, String path, FlowGraph graph, List<FieldProblem> problems) {
        Set<String> before = graph.before(step.key());
        if (!templates.existsById(step.templateId())) {
            problems.add(new FieldProblem(path + ".templateId", "TEMPLATE_NOT_FOUND",
                    "Mail template " + step.templateId() + " does not exist."));
        }
        checkRecipients(step.to(), path + ".to", problems);
        checkRecipients(step.cc(), path + ".cc", problems);
        for (int i = 0; i < step.attachments().size(); i++) {
            String key = step.attachments().get(i);
            if (!before.contains(key) || !(graph.step(key) instanceof ReportStep)) {
                problems.add(new FieldProblem(path + ".attachments[" + i + "]", "UNKNOWN_ATTACHMENT",
                        "'" + key + "' is not a report step that runs before this one."));
            }
        }
    }

    private void checkRecipients(List<String> addresses, String path, List<FieldProblem> problems) {
        for (int i = 0; i < addresses.size(); i++) {
            if (!properties.mail().allows(addresses.get(i))) {
                problems.add(new FieldProblem(path + "[" + i + "]", "RECIPIENT_NOT_ALLOWED",
                        "E-mails may only go to " + String.join(", ", properties.mail().allowedRecipientDomains())
                                + "."));
            }
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
