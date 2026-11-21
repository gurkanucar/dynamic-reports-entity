package com.gucardev.dynamicreportsentity.platform.assistant;

import com.gucardev.dynamicreportsentity.platform.api.ApiException;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.approval.ApprovalService;
import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetColumn;
import com.gucardev.dynamicreportsentity.platform.dataset.saved.SavedDatasetInfo;
import com.gucardev.dynamicreportsentity.platform.dataset.saved.SavedDatasetService;
import com.gucardev.dynamicreportsentity.platform.flow.draft.FlowDraftChecker;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import com.gucardev.dynamicreportsentity.platform.notification.MailTemplate;
import com.gucardev.dynamicreportsentity.platform.notification.MailTemplateService;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Drafts whole flows from a chat ("every Monday mail HR the overdue trainings as a table"): steps, edges, schedules
 * and, when no template fits, new mail templates. The model sees the user's saved datasets, templates, approval
 * groups and catalog (metadata only) and must pass the same checks as saving the flow ({@link FlowDraftChecker}).
 * Nothing is saved here: the UI shows the draft in the editor, creates the new templates and saves on the user's
 * click.
 */
@Component
class FlowAssistant implements Drafter {

    private static final List<Map<String, Object>> TOOLS = tools();
    /** Stands in for a template still to be created while the flow is checked. */
    private static final long NEW_TEMPLATE = -1;

    private final ChatClient chat;
    private final FlowDraftChecker checker;
    private final SavedDatasetService datasets;
    private final MailTemplateService templates;
    private final ApprovalService approvals;
    private final CatalogDescriber catalog;
    private final AssistantProperties properties;
    private final Clock clock;
    private final String prompt;

    FlowAssistant(ChatClient chat, FlowDraftChecker checker, SavedDatasetService datasets, MailTemplateService templates,
                  ApprovalService approvals, CatalogDescriber catalog, AssistantProperties properties, Clock clock) {
        this.chat = chat;
        this.checker = checker;
        this.datasets = datasets;
        this.templates = templates;
        this.approvals = approvals;
        this.catalog = catalog;
        this.properties = properties;
        this.clock = clock;
        String datasetPrompt = MailTemplateAssistant.resource("assistant/dataset-system-prompt.md");
        int from = datasetPrompt.indexOf("# Dataset definition");
        int to = datasetPrompt.indexOf("# Catalog of this user");
        this.prompt = MailTemplateAssistant.resource("assistant/flow-system-prompt.md")
                .replace("{{DATASET_GRAMMAR}}", from < 0 || to < from ? "" : datasetPrompt.substring(from, to).strip());
    }

    @Override
    public String kind() {
        return "flow";
    }

    @Override
    public SuggestionResult draft(List<ChatMessage> conversation, Object current, Actor viewer) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(ToolLoop.message("system", prompt
                .replace("{{TODAY}}", LocalDate.now(clock).toString())
                .replace("{{DATASETS}}", describeDatasets())
                .replace("{{TEMPLATES}}", describeTemplates())
                .replace("{{GROUPS}}", describeGroups())
                .replace("{{CATALOG}}", catalog.describe(viewer))));
        if (current != null) {
            messages.add(ToolLoop.message("system", "Current flow (the user wants to change it):\n"
                    + ToolLoop.JSON.writeValueAsString(current)));
        }
        conversation.forEach(turn -> messages.add(ToolLoop.message(turn.role(), turn.content())));
        return ToolLoop.run(chat, properties, messages, TOOLS, (name, arguments, said, iteration, model) -> {
            switch (name) {
                case "validate_flow", "submit_flow" -> {
                    List<String> problems = check(arguments, viewer);
                    if (!problems.isEmpty()) {
                        return new ToolLoop.ToolAnswer(Map.of("valid", false, "problems", problems));
                    }
                    if (name.equals("validate_flow")) {
                        return new ToolLoop.ToolAnswer(Map.of("valid", true));
                    }
                    Map<String, Object> draft = new LinkedHashMap<>();
                    draft.put("flow", ToolLoop.JSON.treeToValue(arguments.get("flow"), Map.class));
                    draft.put("schedules", list(arguments, "schedules"));
                    draft.put("newTemplates", list(arguments, "newTemplates"));
                    return new ToolLoop.Done(new SuggestionResult(said, ToolLoop.text(arguments.get("flow"), "name"),
                            ToolLoop.text(arguments, "explanation"), null, List.of(), draft, iteration, model));
                }
                default -> {
                    return new ToolLoop.ToolAnswer(Map.of("error",
                            "Unknown tool '" + name + "'. Use validate_flow or submit_flow."));
                }
            }
        }, "I could not draft a flow from that. Could you describe it differently?",
                "The assistant could not draft a valid flow in " + properties.maxIterations() + " attempts.");
    }

    /** Problems of the flow, its new templates and schedules, as lines for the model. */
    private List<String> check(JsonNode arguments, Actor viewer) {
        List<String> problems = new ArrayList<>();
        if (arguments == null || !arguments.path("flow").isObject()) {
            return List.of("Missing 'flow' object.");
        }
        Set<String> refs = new HashSet<>();
        for (JsonNode template : arguments.path("newTemplates")) {
            String ref = template.path("ref").asString("");
            if (ref.isBlank() || !refs.add(ref)) {
                problems.add("newTemplates: every template needs a unique 'ref'.");
            }
            if (template.path("name").asString("").isBlank()) {
                problems.add("newTemplates[" + ref + "].name is required.");
            }
            try {
                templates.preview(template.path("subject").asString(""), template.path("bodyHtml").asString(""), null);
            } catch (ApiException e) {
                problems.add("newTemplates[" + ref + "]: " + (e.errors() == null || e.errors().isEmpty()
                        ? e.getMessage() : e.errors().stream().map(p -> p.field() + " " + p.message())
                        .collect(Collectors.joining("; "))));
            }
        }
        for (JsonNode schedule : arguments.path("schedules")) {
            String cron = schedule.path("cron").asString("").strip();
            if (cron.split("\\s+").length != 5 || !CronExpression.isValidExpression("0 " + cron)) {
                problems.add("schedules: '" + cron + "' is not a valid 5-field cron expression.");
            }
            try {
                ZoneId.of(schedule.path("timeZone").asString("Europe/Istanbul"));
            } catch (DateTimeException e) {
                problems.add("schedules: unknown timeZone '" + schedule.path("timeZone").asString() + "'.");
            }
        }
        ObjectNode flow = (ObjectNode) arguments.get("flow").deepCopy();
        Set<String> placeholders = new HashSet<>();
        JsonNode steps = flow.path("steps");
        if (steps instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                if (array.get(i) instanceof ObjectNode step && step.has("templateRef")) {
                    String ref = step.path("templateRef").asString("");
                    if (!refs.contains(ref)) {
                        problems.add("steps[" + i + "].templateRef '" + ref + "' is not in newTemplates.");
                    }
                    step.remove("templateRef");
                    step.put("templateId", NEW_TEMPLATE);
                    placeholders.add("steps[" + i + "].templateId");
                }
            }
        }
        for (FieldProblem problem : checker.check(flow, viewer)) {
            if (!placeholders.contains(problem.field())) {
                problems.add(problem.field() + ": " + problem.message() + " (" + problem.code() + ")");
            }
        }
        return problems;
    }

    private static List<?> list(JsonNode arguments, String field) {
        JsonNode value = arguments.get(field);
        return value == null || !value.isArray() ? List.of() : ToolLoop.JSON.treeToValue(value, List.class);
    }

    private String describeDatasets() {
        List<SavedDatasetInfo> saved = datasets.list();
        if (saved.isEmpty()) {
            return "(none)";
        }
        return saved.stream().map(dataset -> "- `" + dataset.name() + "`"
                + (dataset.label() == null ? "" : " — " + dataset.label())
                + ": rows of " + dataset.definition().root() + "; columns: "
                + dataset.definition().columns().stream().map(FlowAssistant::columnName)
                .collect(Collectors.joining(", "))).collect(Collectors.joining("\n"));
    }

    private static String columnName(DatasetColumn column) {
        if (column.label() != null) {
            return column.label();
        }
        if (column.aggregate() != null) {
            return column.aggregate() + "(" + Objects.toString(column.field(), "*") + ")";
        }
        return column.field();
    }

    private String describeTemplates() {
        List<MailTemplate> all = templates.list(1, 200).getContent();
        if (all.isEmpty()) {
            return "(none)";
        }
        return all.stream().map(template -> "- id " + template.getId() + ": " + template.getName()
                + " (subject: " + template.getSubject() + ")").collect(Collectors.joining("\n"));
    }

    private String describeGroups() {
        var groups = approvals.groups();
        if (groups.isEmpty()) {
            return "(none)";
        }
        return groups.stream().map(group -> "- id " + group.getId() + ": " + group.getName()
                + (group.getDescription() == null ? "" : " — " + group.getDescription()))
                .collect(Collectors.joining("\n"));
    }

    private static List<Map<String, Object>> tools() {
        Map<String, Object> flow = Map.of("type", "object", "description", "The flow as described in the system prompt.",
                "properties", Map.of(
                        "name", Map.of("type", "string"),
                        "steps", Map.of("type", "array", "items", Map.of("type", "object")),
                        "edges", Map.of("type", "array", "items", Map.of("type", "object"))),
                "required", List.of("name", "steps", "edges"));
        Map<String, Object> schedules = Map.of("type", "array", "items", Map.of("type", "object", "properties",
                Map.of("cron", Map.of("type", "string"), "timeZone", Map.of("type", "string")),
                "required", List.of("cron")));
        Map<String, Object> newTemplates = Map.of("type", "array", "items", Map.of("type", "object", "properties",
                Map.of("ref", Map.of("type", "string"), "name", Map.of("type", "string"),
                        "subject", Map.of("type", "string"), "bodyHtml", Map.of("type", "string")),
                "required", List.of("ref", "name", "subject", "bodyHtml")));
        Map<String, Object> draft = Map.of("flow", flow, "schedules", schedules, "newTemplates", newTemplates);
        Map<String, Object> submit = new LinkedHashMap<>(draft);
        submit.put("explanation", Map.of("type", "string",
                "description", "For the user, in their language: what the flow does, when, and what to check"));
        return List.of(
                ToolLoop.function("validate_flow", "Checks a draft flow (with its schedules and new templates) like "
                                + "saving it would. Returns {valid} or {valid: false, problems}.",
                        Map.of("type", "object", "properties", draft, "required", List.of("flow"))),
                ToolLoop.function("submit_flow", "Hands in the finished flow draft.",
                        Map.of("type", "object", "properties", submit, "required", List.of("flow", "explanation"))));
    }
}
