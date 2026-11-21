package com.gucardev.dynamicreportsentity.platform.assistant.web;

import com.gucardev.dynamicreportsentity.platform.assistant.SuggestionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Change this step": {@code current} is {@code {flow: {name, steps, edges}, stepKey}}; the reply's {@code draft} is
 * {@code {step, newTemplates}}, checked inside the flow. Nothing is saved. Contract: {@code api/assistant-api.yaml}.
 */
@RestController
@RequestMapping(StepSuggestionController.PATH)
@PreAuthorize("hasAuthority('PLATFORM_USER')")
class StepSuggestionController extends SuggestionEndpoints {

    static final String PATH = "/api/step-suggestions";

    StepSuggestionController(SuggestionService suggestions) {
        super(suggestions, "step", PATH);
    }
}
