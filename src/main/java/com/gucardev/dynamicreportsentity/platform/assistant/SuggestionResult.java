package com.gucardev.dynamicreportsentity.platform.assistant;

import com.gucardev.dynamicreportsentity.platform.reporting.query.ReportDefinition;

import java.util.List;
import java.util.Map;

/**
 * What the assistant came back with: a validated report (title, explanation, definition and its output columns as
 * {@code {label, type}}) or only a {@code message}, e.g. a clarifying question.
 *
 * @param iterations model calls it took
 */
public record SuggestionResult(String message, String title, String explanation, ReportDefinition definition,
                               List<Map<String, String>> columns, int iterations, String model) {
}
