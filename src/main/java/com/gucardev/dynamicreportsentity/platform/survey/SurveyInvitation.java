package com.gucardev.dynamicreportsentity.platform.survey;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A link to a survey that something waits on (e.g. a flow step): the response sent through it is reported with the
 * invitation's source, so the waiting flow goes on with those answers. Single use; revoked when the wait ends.
 */
@Entity
@Table(name = "survey_invitation",
        uniqueConstraints = @UniqueConstraint(name = "uk_survey_invitation_source", columnNames = {"source_type", "source_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyInvitation {

    @Id
    @Column(length = 64)
    private String token;

    @Column(name = "survey_id", nullable = false)
    private UUID surveyId;

    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;

    @Column(name = "source_id", nullable = false, length = 100)
    private String sourceId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private UUID responseId;

    private boolean revoked;

    SurveyInvitation(String token, UUID surveyId, String sourceType, String sourceId, Instant now, Instant expiresAt) {
        this.token = token;
        this.surveyId = surveyId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    boolean usable(Instant now) {
        return !revoked && responseId == null && expiresAt.isAfter(now);
    }

    void use(UUID responseId) {
        this.responseId = responseId;
    }

    void revoke() {
        this.revoked = true;
    }
}
