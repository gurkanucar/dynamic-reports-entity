package com.gucardev.dynamicreportsentity.platform.survey;

import java.time.Instant;
import java.util.Map;

/**
 * A response was accepted (published after its transaction committed, at least once).
 *
 * @param respondent who answered; null for anonymous surveys
 * @param answers    by question id: option ids, numbers, booleans, texts, ISO dates
 * @param display    the same answers as people read them (option labels, Evet/Hayır)
 * @param labels     question texts by id
 */
public record SurveyResponseSubmitted(long surveyId, String surveyTitle, String surveyOwner, long responseId,
                                      String respondent, Map<String, Object> answers, Map<String, String> display,
                                      Map<String, String> labels, Instant submittedAt) {
}
