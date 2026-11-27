package com.gucardev.dynamicreportsentity.platform.survey;

import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** The owner's (or a colleague's) note on a response: what was done, who was called. */
@Entity
@Table(name = "survey_response_note", indexes = @Index(name = "ix_survey_note_response", columnList = "response_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyResponseNote {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "survey_id", nullable = false)
    private UUID surveyId;

    @Column(name = "response_id", nullable = false)
    private UUID responseId;

    @Column(nullable = false, length = 100)
    private String author;

    @Column(nullable = false, length = 2000)
    private String text;

    @Column(nullable = false)
    private Instant createdAt;

    SurveyResponseNote(SurveyResponse response, String author, String text, Instant now) {
        this.surveyId = response.getSurveyId();
        this.responseId = response.getId();
        this.author = author;
        this.text = text;
        this.createdAt = now;
    }
}
