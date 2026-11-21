package com.gucardev.dynamicreportsentity.platform.assistant.web;

import com.gucardev.dynamicreportsentity.platform.assistant.SuggestionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Describe the survey": the assistant designs a survey (questions, sections, follow-ups, rules); the reply's
 * {@code draft} is {@code {title, description, definition, assumptions, suggestions}}, not saved.
 * Contract: {@code api/assistant-api.yaml}.
 */
@RestController
@RequestMapping(SurveySuggestionController.PATH)
@PreAuthorize("hasAuthority('PLATFORM_USER')")
class SurveySuggestionController extends SuggestionEndpoints {

    static final String PATH = "/api/survey-suggestions";

    SurveySuggestionController(SuggestionService suggestions) {
        super(suggestions, "survey", PATH);
    }
}
