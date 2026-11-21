package com.gucardev.dynamicreportsentity.platform.assistant.web;

import com.gucardev.dynamicreportsentity.platform.assistant.SuggestionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The help chat: questions about the platform's pages; {@code current} is {@code {path, title}} of the page the user
 * is on. The reply's {@code message} is the answer, its {@code draft} {@code {links, handoff}}.
 * Contract: {@code api/assistant-api.yaml}.
 */
@RestController
@RequestMapping(HelpSuggestionController.PATH)
@PreAuthorize("hasAuthority('PLATFORM_USER')")
class HelpSuggestionController extends SuggestionEndpoints {

    static final String PATH = "/api/help-suggestions";

    HelpSuggestionController(SuggestionService suggestions) {
        super(suggestions, "help", PATH);
    }
}
