package com.gucardev.dynamicreportsentity.platform.survey;

import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * One person's answers to a survey. The answers never change once sent; beside them the owner keeps the follow-up
 * (status, priority, follow-up date, assignee, extra tracking fields), which respondents never see.
 */
@Entity
@Table(name = "survey_response",
        uniqueConstraints = @UniqueConstraint(name = "uk_survey_response_respondent",
                columnNames = {"survey_id", "respondent_key"}),
        indexes = @Index(name = "ix_survey_response_survey", columnList = "survey_id, submitted_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyResponse {

    /** Where the owner is with a response; in JSON {@code new}, {@code in_progress}, ... */
    public enum FollowStatus {
        @JsonProperty("new") NEW,
        @JsonProperty("in_progress") IN_PROGRESS,
        @JsonProperty("resolved") RESOLVED,
        @JsonProperty("unresolved") UNRESOLVED
    }

    public enum Priority {
        @JsonProperty("low") LOW,
        @JsonProperty("normal") NORMAL,
        @JsonProperty("high") HIGH
    }

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "survey_id", nullable = false)
    private UUID surveyId;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "follow_status", nullable = false, length = 20)
    private FollowStatus followStatus = FollowStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Priority priority;

    /** 1 low, 2 normal, 3 high: what "sort by priority" orders by (the names would sort alphabetically). */
    private Integer priorityRank;

    private LocalDate followUpAt;

    @Column(length = 100)
    private String assignee;

    /** Values of the survey's extra tracking fields, by field id. */
    @Column(name = "tracking_json", length = 20_000)
    private String trackingJson;

    /** What waited for this response (e.g. {@code flow-step}), through an invitation; null otherwise. */
    @Column(name = "source_type", length = 40)
    private String sourceType;

    @Column(name = "source_id", length = 100)
    private String sourceId;

    private Instant updatedAt;

    @Column(length = 100)
    private String updatedBy;

    SurveyResponse(Survey survey, String respondent, String respondentKey, Map<String, Object> answers, Instant now) {
        this.surveyId = survey.getId();
        this.surveyVersion = survey.getVersion();
        this.respondent = respondent;
        this.respondentKey = respondentKey;
        this.answersJson = SurveyDefinition.JSON.writeValueAsString(answers);
        this.submittedAt = now;
    }

    void cameThrough(SurveyInvitation invitation) {
        this.sourceType = invitation.getSourceType();
        this.sourceId = invitation.getSourceId();
    }

    void followUp(FollowStatus status, Priority priority, LocalDate followUpAt, String assignee,
                  Map<String, Object> tracking, String editor, Instant now) {
        this.followStatus = status;
        this.priority = priority;
        this.priorityRank = priority == null ? null : priority.ordinal() + 1;
        this.followUpAt = followUpAt;
        this.assignee = assignee;
        this.trackingJson = tracking == null || tracking.isEmpty() ? null
                : SurveyDefinition.JSON.writeValueAsString(tracking);
        this.updatedBy = editor;
        this.updatedAt = now;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> tracking() {
        return trackingJson == null ? Map.of() : SurveyDefinition.JSON.readValue(trackingJson, Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> answers() {
        return SurveyDefinition.JSON.readValue(answersJson, Map.class);
    }
}
