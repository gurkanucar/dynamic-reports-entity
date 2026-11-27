package com.gucardev.dynamicreportsentity.platform.survey;

import java.util.UUID;
import java.time.Instant;
import java.util.Map;

/**
 * A response was accepted (published after its transaction committed, at least once).
 *
 * @param respondent who answered; null for anonymous surveys
 * @param answers    by question id: option ids, numbers, booleans, texts, ISO dates
 * @param display    the same answers as people read them (option labels, Evet/Hayır)
 * @param labels     question texts by id
 * @param sourceType what waited for this response through an invitation (e.g. {@code flow-step}); null otherwise
 */
public record SurveyResponseSubmitted(UUID surveyId, String surveyTitle, String surveyOwner, UUID responseId,
                                      String respondent, Map<String, Object> answers, Map<String, String> display,
                                      Map<String, String> labels, Instant submittedAt, String sourceType,
                                      String sourceId) {
}
