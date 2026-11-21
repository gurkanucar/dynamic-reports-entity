package com.gucardev.dynamicreportsentity.platform.assistant.web;

import com.gucardev.dynamicreportsentity.platform.assistant.SuggestionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Describe the dataset you need": the assistant builds a validated dataset definition.
 * Contract: {@code api/assistant-api.yaml}.
 */
@RestController
@RequestMapping(DatasetSuggestionController.PATH)
@PreAuthorize("hasAuthority('PLATFORM_USER')")
class DatasetSuggestionController extends SuggestionEndpoints {

    static final String PATH = "/api/dataset-suggestions";

    DatasetSuggestionController(SuggestionService suggestions) {
        super(suggestions, "dataset", PATH);
    }
}
