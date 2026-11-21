package com.gucardev.dynamicreportsentity.platform.notification;

import java.util.UUID;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.Kind;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
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
import java.util.Optional;

/**
 * Mail templates. Everybody with PLATFORM_USER may read and use them; the owner or a PLATFORM_ADMIN may change or
 * delete one. A template some module still uses ({@link MailTemplateUsage}) cannot be deleted.
 */
@Service
public class MailTemplateService {

    public static final String ADMIN_AUTHORITY = "PLATFORM_ADMIN";

    /** Fields of a template as entered by the user. */
    public record TemplateInput(String name, String description, String subject, String bodyHtml) {
    }

    /** A template filled in with data. */
    public record RenderedMail(String subject, String html) {
    }

    private final MailTemplateRepository templates;
    private final List<MailTemplateUsage> usages;
    private final TemplateRenderer renderer;
    private final Clock clock;

    MailTemplateService(MailTemplateRepository templates, List<MailTemplateUsage> usages, TemplateRenderer renderer,
                        Clock clock) {
        this.templates = templates;
        this.usages = usages;
        this.renderer = renderer;
        this.clock = clock;
    }

    /** Sortable fields of the template list (API name → property). */
    public static final java.util.Map<String, String> SORTS = java.util.Map.of("name", "name",
            "updatedAt", "updatedAt", "id", "id");

    public Page<MailTemplate> list(int page, int size) {
        return list(null, null, page, size);
    }

    /** {@code q} over name, subject, description; {@code sort} default {@code name}. */
    public Page<MailTemplate> list(String q, String sort, int page, int size) {
        return templates.search(com.gucardev.dynamicreportsentity.platform.api.ListQuery.like(q),
                com.gucardev.dynamicreportsentity.platform.api.ListQuery.page(page, size,
                        com.gucardev.dynamicreportsentity.platform.api.ListQuery.sort(sort, SORTS, "name")));
    }

    public MailTemplate get(UUID id) {
        return templates.findById(id).orElseThrow(() -> notFound(id));
    }

    @Transactional
    public MailTemplate create(TemplateInput input, Actor editor) {
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
    public MailTemplate update(UUID id, long expectedVersion, TemplateInput input, Actor editor) {
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
    public void delete(UUID id, Actor editor) {
        MailTemplate template = get(id);
        guardChange(template, editor);
        if (usages.stream().anyMatch(usage -> usage.usesTemplate(id))) {
            throw new NotificationException(Kind.CONFLICT, "TEMPLATE_IN_USE",
                    "The template is used by at least one flow; change those flows first.");
        }
        templates.delete(template);
    }

    public boolean exists(UUID id) {
        return id != null && templates.existsById(id);   // a step that has not picked one yet
    }

    /**
     * Fills in the template with {@code model}: subject as plain text, body as escaped HTML with its {@code <style>}
     * rules inlined ({@link CssInliner}); empty if it is gone.
     */
    public Optional<RenderedMail> render(UUID id, Map<String, ?> model) {
        return templates.findById(id).map(template -> new RenderedMail(renderer.renderText(template.getSubject(), model),
                CssInliner.inline(renderer.renderHtml(template.getBodyHtml(), model))));
    }

    /** Renders a draft with {@code model}, or with {@link #sampleModel()} if none is given. */
    public Map<String, String> preview(String subject, String bodyHtml, Map<String, Object> model) {
        List<FieldProblem> problems = new ArrayList<>();
        checkSyntax("subject", subject, problems);
        checkSyntax("bodyHtml", bodyHtml, problems);
        if (!problems.isEmpty()) {
            throw NotificationException.invalid(problems);
        }
        Map<String, Object> data = model == null ? sampleModel() : model;
        return Map.of("subject", renderer.renderText(subject, data), "bodyHtml",
                CssInliner.inline(renderer.renderHtml(bodyHtml, data)));
    }

    /** What a template sees in a flow with one dataset step named {@code dataset}. */
    public Map<String, Object> sampleModel() {
        return Map.of(
                "flow", Map.of("id", 1, "name", "Weekly headcount"),
                "run", Map.of("id", 42, "trigger", "schedule", "triggeredBy", "schedule:7",
                        "startedAt", clock.instant().toString(), "date", LocalDate.now(clock).toString()),
                "steps", Map.of("dataset", Map.of(
                        "rowCount", 2, "hasRows", true, "fileName", "dataset.csv",
                        "columns", List.of("Department", "Headcount"),
                        "rows", List.of(Map.of("cells", List.of("Software Development", "12")),
                                Map.of("cells", List.of("Human Resources", "5"))),
                        "previewTruncated", false)));
    }

    private void check(TemplateInput input, UUID id) {
        List<FieldProblem> problems = new ArrayList<>();
        checkSyntax("subject", input.subject(), problems);
        checkSyntax("bodyHtml", input.bodyHtml(), problems);
        if (!problems.isEmpty()) {
            throw NotificationException.invalid(problems);
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

    private static void guardChange(MailTemplate template, Actor editor) {
        if (!template.getOwner().equals(editor.username()) && !editor.hasAuthority(ADMIN_AUTHORITY)) {
            throw new NotificationException(Kind.FORBIDDEN, "NOT_TEMPLATE_OWNER",
                    "Only the owner of the template or a dataset admin may change it.");
        }
    }

    private static NotificationException notFound(UUID id) {
        return new NotificationException(Kind.NOT_FOUND, "TEMPLATE_NOT_FOUND", "Mail template " + id + " does not exist.");
    }

    private static NotificationException duplicateName() {
        return new NotificationException(Kind.CONFLICT, "TEMPLATE_NAME_TAKEN", "Another template already has this name.");
    }

    private static NotificationException versionMismatch() {
        return new NotificationException(Kind.PRECONDITION_FAILED, "VERSION_MISMATCH",
                "The template was changed by someone else; reload it and try again.");
    }
}
