package com.gucardev.dynamicreportsentity.platform.assistant.web;

import com.gucardev.dynamicreportsentity.platform.assistant.SuggestionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Her pazartesi 9'da" → {@code 0 9 * * MON}: the reply's {@code draft} is {@code {cron, description}}, checked to be
 * valid and at most every 5 minutes. Contract: {@code api/assistant-api.yaml}.
 */
@RestController
@RequestMapping(CronSuggestionController.PATH)
@PreAuthorize("hasAuthority('PLATFORM_USER')")
class CronSuggestionController extends SuggestionEndpoints {

    static final String PATH = "/api/cron-suggestions";

    CronSuggestionController(SuggestionService suggestions) {
        super(suggestions, "cron", PATH);
    }
}
