package com.gucardev.dynamicreportsentity.platform.survey;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A questionnaire and who may answer it how. Only its owner (and platform admins) see and change it. */
@Entity
@Table(name = "survey", indexes = {
        @Index(name = "ix_survey_owner", columnList = "owner"),
        @Index(name = "ix_survey_status", columnList = "status")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Survey {

    /** In JSON as {@code draft} / {@code published} / {@code closed}. */
    public enum Status {
        /** Being designed: only the owner can open it (to try it). */
        @JsonProperty("draft") DRAFT,
        /** Takes responses (until {@code closesAt}, if set). */
        @JsonProperty("published") PUBLISHED,
        /** Takes no more responses; its responses stay. */
        @JsonProperty("closed") CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "definition_json", nullable = false, length = 200_000)
    private String definitionJson;

    /** Responses do not record who sent them. */
    private boolean anonymous;

    /** Each user may answer once (also when anonymous: a hash tells repeats apart, not who answered). */
    private boolean oneResponsePerUser;

    private Instant closesAt;

    @Column(nullable = false, length = 100)
    private String owner;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false, length = 100)
    private String updatedBy;

    private Instant publishedAt;

    @Column(name = "settings_json", length = 20_000)
    private String settingsJson;

    Survey(String owner, Instant now) {
        this.owner = owner;
        this.createdAt = now;
    }

    void define(SurveyInput input, String editor, Instant now) {
        this.title = input.title().strip();
        this.description = input.description() == null || input.description().isBlank() ? null
                : input.description().strip();
        if (input.status() == Status.PUBLISHED && publishedAt == null) {
            this.publishedAt = now;
        }
        this.status = input.status();
        this.definitionJson = input.definition().toJson();
        this.anonymous = input.anonymous();
        this.oneResponsePerUser = input.oneResponsePerUser();
        this.closesAt = input.closesAt();
        this.settingsJson = (input.settings() == null ? SurveySettings.DEFAULT : input.settings()).toJson();
        this.updatedBy = editor;
        this.updatedAt = now;
    }

    public SurveyDefinition definition() {
        return SurveyDefinition.fromJson(definitionJson);
    }

    public SurveySettings settings() {
        return SurveySettings.fromJson(settingsJson);
    }

    /** Whether it takes responses now. */
    public boolean open(Instant now) {
        return status == Status.PUBLISHED && (closesAt == null || closesAt.isAfter(now));
    }
}
