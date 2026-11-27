package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.flow.draft.FlowDraftChecker;
import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowStep;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Same checks as saving a flow: the request's bean validation, then {@link FlowService}'s step and graph rules. */
@Component
class DraftChecker implements FlowDraftChecker {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final FlowService flows;
    private final Validator validator;

    DraftChecker(FlowService flows, Validator validator) {
        this.flows = flows;
        this.validator = validator;
    }

    @Override
    public List<FieldProblem> check(JsonNode flow, Actor editor) {
        List<FieldProblem> problems = new ArrayList<>();
        if (flow == null || !flow.isObject()) {
            return List.of(new FieldProblem("flow", "REQUIRED", "Missing flow object."));
        }
        String name = flow.path("name").asString("");
        if (name.isBlank() || name.length() > 100) {
            problems.add(new FieldProblem("name", "INVALID", "1-100 characters."));
        }
        List<FlowStep> steps = new ArrayList<>();
        JsonNode stepNodes = flow.path("steps");
        if (!stepNodes.isArray() || stepNodes.isEmpty()) {
            problems.add(new FieldProblem("steps", "REQUIRED", "At least one step."));
            return problems;
        }
        for (int i = 0; i < stepNodes.size(); i++) {
            try {
                FlowStep step = JSON.treeToValue(stepNodes.get(i), FlowStep.class);
                String path = "steps[" + i + "]";
                validator.validate(step).stream()
                        .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                        .forEach(violation -> problems.add(new FieldProblem(path + "." + violation.getPropertyPath(),
                                "INVALID", violation.getMessage())));
                steps.add(step);
            } catch (JacksonException | IllegalArgumentException e) {
                problems.add(new FieldProblem("steps[" + i + "]", "INVALID_STEP", e.getMessage()));
            }
        }
        List<FlowEdge> edges = new ArrayList<>();
        for (JsonNode edge : flow.path("edges")) {
            try {
                edges.add(JSON.treeToValue(edge, FlowEdge.class));
            } catch (JacksonException e) {
                problems.add(new FieldProblem("edges", "INVALID_EDGE", e.getMessage()));
            }
        }
        if (!problems.isEmpty()) {
            return problems;
        }
        return flows.problems(new FlowService.FlowInput(name, null, steps, edges, Map.of()), editor);
    }
}
