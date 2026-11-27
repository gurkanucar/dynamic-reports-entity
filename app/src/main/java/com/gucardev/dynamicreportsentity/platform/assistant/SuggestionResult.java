package com.gucardev.dynamicreportsentity.platform.assistant;

import com.gucardev.entityreports.Dataset;

import java.util.List;
import java.util.Map;

/**
 * What the assistant came back with: a validated draft or only a {@code message}, e.g. a clarifying question.
 * A dataset comes as {@code definition} with its output {@code columns} ({@code {label, type}}); a mail template or a
 * flow as {@code draft}.
 *
 * @param iterations model calls it took
 */
public record SuggestionResult(String message, String title, String explanation, Dataset.Definition definition,
                               List<Map<String, String>> columns, Map<String, Object> draft, int iterations,
                               String model) {

    static SuggestionResult message(String message, int iterations, String model) {
        return new SuggestionResult(message, null, null, null, List.of(), null, iterations, model);
    }
}
