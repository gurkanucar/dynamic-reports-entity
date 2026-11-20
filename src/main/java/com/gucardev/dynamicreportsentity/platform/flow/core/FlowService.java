package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.core.FlowException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowException.Kind;
import com.gucardev.dynamicreportsentity.platform.flow.mail.MailTemplateRepository;
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
    public record FlowInput(String name, String description, List<FlowStep> steps) {
    }

    private final FlowRepository flows;
    private final FlowScheduleRepository schedules;
    private final FlowRunRepository runs;
    private final MailTemplateRepository templates;
    private final ReportExecutor reports;
    private final RunAsResolver runAs;
    private final FlowProperties properties;
    private final Clock clock;

    FlowService(FlowRepository flows, FlowScheduleRepository schedules, FlowRunRepository runs,
                MailTemplateRepository templates, ReportExecutor reports, RunAsResolver runAs,
                FlowProperties properties, Clock clock) {
        this.flows = flows;
        this.schedules = schedules;
        this.runs = runs;
        this.templates = templates;
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
        Set<String> keys = new HashSet<>();
        Set<String> reportKeys = new HashSet<>();
        for (int i = 0; i < steps.size(); i++) {
            FlowStep step = steps.get(i);
            String path = "steps[" + i + "]";
            if (!keys.add(step.key())) {
                problems.add(new FieldProblem(path + ".key", "DUPLICATE_STEP_KEY",
                        "Another step already uses the key '" + step.key() + "'."));
            }
            switch (step) {
                case ReportStep report -> {
                    checkReport(report, path, editor, owner, problems);
                    reportKeys.add(report.key());
                }
                case MailStep mail -> checkMail(mail, path, reportKeys, problems);
            }
        }
        if (!problems.isEmpty()) {
            throw FlowException.invalid(problems);
        }
        return new FlowDefinition(steps);
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

    private void checkMail(MailStep step, String path, Set<String> earlierReports, List<FieldProblem> problems) {
        if (!templates.existsById(step.templateId())) {
            problems.add(new FieldProblem(path + ".templateId", "TEMPLATE_NOT_FOUND",
                    "Mail template " + step.templateId() + " does not exist."));
        }
        checkRecipients(step.to(), path + ".to", problems);
        checkRecipients(step.cc(), path + ".cc", problems);
        for (int i = 0; i < step.attachments().size(); i++) {
            String key = step.attachments().get(i);
            if (!earlierReports.contains(key)) {
                problems.add(new FieldProblem(path + ".attachments[" + i + "]", "UNKNOWN_ATTACHMENT",
                        "'" + key + "' is not a report step before this one."));
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
