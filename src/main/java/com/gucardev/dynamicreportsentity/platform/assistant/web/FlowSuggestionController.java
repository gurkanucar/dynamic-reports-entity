package com.gucardev.dynamicreportsentity.platform.assistant.web;

import com.gucardev.dynamicreportsentity.platform.assistant.SuggestionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Describe what should happen": the assistant drafts a flow. The reply's {@code draft} is
 * {@code {flow: {name, steps, edges}, schedules: [{cron, timeZone}], newTemplates: [{ref, name, subject, bodyHtml}]}};
 * mail steps may refer to a new template by {@code templateRef}. Nothing is saved. Contract:
 * {@code api/assistant-api.yaml}.
 */
@RestController
@RequestMapping(FlowSuggestionController.PATH)
@PreAuthorize("hasAuthority('PLATFORM_USER')")
class FlowSuggestionController extends SuggestionEndpoints {

    static final String PATH = "/api/flow-suggestions";

    FlowSuggestionController(SuggestionService suggestions) {
        super(suggestions, "flow", PATH);
    }
}
