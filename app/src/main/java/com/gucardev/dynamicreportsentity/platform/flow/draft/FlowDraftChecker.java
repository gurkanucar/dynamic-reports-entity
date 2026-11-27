package com.gucardev.dynamicreportsentity.platform.flow.draft;

import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import tools.jackson.databind.JsonNode;

import java.util.List;

/** Checks a flow as JSON ({@code {name, steps, edges}}, the body of {@code POST /api/flows}) without saving it. */
public interface FlowDraftChecker {

    /** Every problem saving it as {@code editor} would report; empty = it would save. */
    List<FieldProblem> check(JsonNode flow, Actor editor);
}
