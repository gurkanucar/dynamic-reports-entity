package com.gucardev.dynamicreportsentity.platform.assistant.web;

import com.gucardev.dynamicreportsentity.platform.assistant.ReportSuggestion;
import com.gucardev.dynamicreportsentity.platform.assistant.SuggestionResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A suggestion as the API shows it. {@code reply} is set once {@code status} is {@code completed}: either a report
 * ({@code definition} not null) or only a {@code message} (a question back to the user).
 */
record SuggestionView(long id, String status, String createdAt, String finishedAt, String model, Reply reply,
                      Error error) {

    /** @param definition the report definition without empty (null) properties, ready to edit and run */
    record Reply(String message, String title, String explanation, JsonNode definition,
                 List<Map<String, String>> columns) {
    }

    private static final JsonMapper JSON = JsonMapper.builder()
            .changeDefaultPropertyInclusion(include -> include.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    record Error(String code, String message) {
    }

    static SuggestionView of(ReportSuggestion suggestion, SuggestionResult result) {
        return new SuggestionView(suggestion.getId(), suggestion.getStatus().name().toLowerCase(Locale.ROOT),
                time(suggestion.getCreatedAt()), time(suggestion.getFinishedAt()), suggestion.getModel(),
                result == null ? null : new Reply(result.message(), result.title(), result.explanation(),
                        result.definition() == null ? null : JSON.valueToTree(result.definition()),
                        result.columns() == null ? List.of() : result.columns()),
                suggestion.getErrorCode() == null ? null
                        : new Error(suggestion.getErrorCode(), suggestion.getErrorMessage()));
    }

    private static String time(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
