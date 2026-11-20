package com.gucardev.dynamicreportsentity.platform.flow.mail;

import com.gucardev.dynamicreportsentity.platform.flow.core.FlowException;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowException.Kind;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowRepository;
import com.gucardev.dynamicreportsentity.platform.reporting.ReportViewer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mail templates. Everybody with REPORT_RUN may read and use them; the owner or a REPORT_ADMIN may change or
 * delete one. A template used by a flow cannot be deleted.
 */
@Service
public class MailTemplateService {

    public static final String ADMIN_AUTHORITY = "REPORT_ADMIN";

    /** Fields of a template as entered by the user. */
    public record TemplateInput(String name, String description, String subject, String bodyHtml) {
    }

    private final MailTemplateRepository templates;
    private final FlowRepository flows;
    private final TemplateRenderer renderer;
    private final Clock clock;

    MailTemplateService(MailTemplateRepository templates, FlowRepository flows, TemplateRenderer renderer,
                        Clock clock) {
        this.templates = templates;
        this.flows = flows;
        this.renderer = renderer;
        this.clock = clock;
    }

    public Page<MailTemplate> list(int page, int size) {
        return templates.findAll(PageRequest.of(page - 1, size, Sort.by("name", "id")));
    }

    public MailTemplate get(long id) {
        return templates.findById(id).orElseThrow(() -> notFound(id));
    }

    @Transactional
    public MailTemplate create(TemplateInput input, ReportViewer editor) {
        check(input, null);
        MailTemplate template = new MailTemplate(editor.username(), clock.instant());
        template.define(input.name().strip(), input.description(), input.subject(), input.bodyHtml(),
                editor.username(), clock.instant());
        try {
            return templates.saveAndFlush(template);
        } catch (DataIntegrityViolationException e) {
            throw duplicateName();     // created concurrently with the same name
        }
    }

    @Transactional
    public MailTemplate update(long id, long expectedVersion, TemplateInput input, ReportViewer editor) {
        MailTemplate template = get(id);
        guardChange(template, editor);
        if (template.getVersion() != expectedVersion) {
            throw versionMismatch();
        }
        check(input, id);
        template.define(input.name().strip(), input.description(), input.subject(), input.bodyHtml(),
                editor.username(), clock.instant());
        try {
            return templates.saveAndFlush(template);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw versionMismatch();
        } catch (DataIntegrityViolationException e) {
            throw duplicateName();
        }
    }

    @Transactional
    public void delete(long id, ReportViewer editor) {
        MailTemplate template = get(id);
        guardChange(template, editor);
        if (flows.existsByTemplateIdsContaining(id)) {
            throw new FlowException(Kind.CONFLICT, "TEMPLATE_IN_USE",
                    "The template is used by at least one flow; change those flows first.");
        }
        templates.delete(template);
    }

    /** Renders a draft with {@code model}, or with {@link #sampleModel()} if none is given. */
    public Map<String, String> preview(String subject, String bodyHtml, Map<String, Object> model) {
        List<FieldProblem> problems = new ArrayList<>();
        checkSyntax("subject", subject, problems);
        checkSyntax("bodyHtml", bodyHtml, problems);
        if (!problems.isEmpty()) {
            throw FlowException.invalid(problems);
        }
        Map<String, Object> data = model == null ? sampleModel() : model;
        return Map.of("subject", renderer.renderText(subject, data), "bodyHtml", renderer.renderHtml(bodyHtml, data));
    }

    /** What a template sees in a flow with one report step named {@code report}. */
    public Map<String, Object> sampleModel() {
        return Map.of(
                "flow", Map.of("id", 1, "name", "Weekly headcount"),
                "run", Map.of("id", 42, "trigger", "schedule", "triggeredBy", "schedule:7",
                        "startedAt", clock.instant().toString(), "date", LocalDate.now(clock).toString()),
                "steps", Map.of("report", Map.of(
                        "rowCount", 2, "hasRows", true, "fileName", "report.csv",
                        "columns", List.of("Department", "Headcount"),
                        "rows", List.of(Map.of("cells", List.of("Software Development", "12")),
                                Map.of("cells", List.of("Human Resources", "5"))),
                        "previewTruncated", false)));
    }

    private void check(TemplateInput input, Long id) {
        List<FieldProblem> problems = new ArrayList<>();
        checkSyntax("subject", input.subject(), problems);
        checkSyntax("bodyHtml", input.bodyHtml(), problems);
        if (!problems.isEmpty()) {
            throw FlowException.invalid(problems);
        }
        String name = input.name().strip();
        if (id == null ? templates.existsByNameIgnoreCase(name) : templates.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicateName();
        }
    }

    private void checkSyntax(String field, String template, List<FieldProblem> problems) {
        try {
            renderer.check(template);
        } catch (TemplateRenderer.TemplateSyntaxException e) {
            problems.add(new FieldProblem(field, "TEMPLATE_SYNTAX_ERROR", e.getMessage()));
        }
    }

    private static void guardChange(MailTemplate template, ReportViewer editor) {
        if (!template.getOwner().equals(editor.username()) && !editor.hasAuthority(ADMIN_AUTHORITY)) {
            throw new FlowException(Kind.FORBIDDEN, "NOT_TEMPLATE_OWNER",
                    "Only the owner of the template or a report admin may change it.");
        }
    }

    private static FlowException notFound(long id) {
        return FlowException.notFound("TEMPLATE_NOT_FOUND", "Mail template " + id + " does not exist.");
    }

    private static FlowException duplicateName() {
        return new FlowException(Kind.CONFLICT, "TEMPLATE_NAME_TAKEN", "Another template already has this name.");
    }

    private static FlowException versionMismatch() {
        return new FlowException(Kind.PRECONDITION_FAILED, "VERSION_MISMATCH",
                "The template was changed by someone else; reload it and try again.");
    }
}
