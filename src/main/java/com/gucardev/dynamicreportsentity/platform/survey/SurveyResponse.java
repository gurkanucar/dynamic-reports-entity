package com.gucardev.dynamicreportsentity.platform.survey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/** One person's answers to a survey; never changed once sent. */
@Entity
@Table(name = "survey_response",
        uniqueConstraints = @UniqueConstraint(name = "uk_survey_response_respondent",
                columnNames = {"survey_id", "respondent_key"}),
        indexes = @Index(name = "ix_survey_response_survey", columnList = "survey_id, submitted_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "survey_id", nullable = false)
    private Long surveyId;

    /** The survey's version when it was answered. */
    @Column(nullable = false)
    private long surveyVersion;

    /** Who answered; null for anonymous surveys. */
    @Column(length = 100)
    private String respondent;

    /** For "one response per user": a hash of survey and user, unique per survey; null otherwise. */
    @Column(name = "respondent_key", length = 64)
    private String respondentKey;

    @Column(name = "answers_json", nullable = false, length = 100_000)
    private String answersJson;

    @Column(nullable = false)
    private Instant submittedAt;

    SurveyResponse(Survey survey, String respondent, String respondentKey, Map<String, Object> answers, Instant now) {
        this.surveyId = survey.getId();
        this.surveyVersion = survey.getVersion();
        this.respondent = respondent;
        this.respondentKey = respondentKey;
        this.answersJson = SurveyDefinition.JSON.writeValueAsString(answers);
        this.submittedAt = now;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> answers() {
        return SurveyDefinition.JSON.readValue(answersJson, Map.class);
    }
}
