package com.gucardev.dynamicreportsentity.platform.assistant.web;

import com.gucardev.dynamicreportsentity.platform.assistant.SuggestionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Describe the e-mail": the assistant writes a mail template (subject, styled HTML body); the reply's {@code draft}
 * is {@code {name, description, subject, bodyHtml}}, not saved. Contract: {@code api/assistant-api.yaml}.
 */
@RestController
@RequestMapping(MailTemplateSuggestionController.PATH)
@PreAuthorize("hasAuthority('PLATFORM_USER')")
class MailTemplateSuggestionController extends SuggestionEndpoints {

    static final String PATH = "/api/mail-template-suggestions";

    MailTemplateSuggestionController(SuggestionService suggestions) {
        super(suggestions, "mail-template", PATH);
    }
}
