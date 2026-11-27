package com.gucardev.dynamicreportsentity.platform.survey;

import java.time.Instant;

/** A survey as the owner entered it. */
public record SurveyInput(String title, String description, Survey.Status status, SurveyDefinition definition,
                          boolean anonymous, boolean oneResponsePerUser, Instant closesAt, SurveySettings settings) {
}
