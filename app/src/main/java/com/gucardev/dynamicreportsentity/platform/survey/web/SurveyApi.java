package com.gucardev.dynamicreportsentity.platform.survey.web;

import java.util.UUID;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.survey.Survey;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyDefinition;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyInput;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyResponse;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyService;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyResponseNote;
import com.gucardev.dynamicreportsentity.platform.survey.SurveySettings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.value;

/** Request and response bodies of the survey API ({@code api/surveys-api.yaml}). */
final class SurveyApi {

    private SurveyApi() {
    }

    record SurveyRequest(@NotBlank @Size(max = 200) String title, @Size(max = 2000) String description,
                         @NotNull Survey.Status status, @NotNull Map<String, Object> definition,
                         Boolean anonymous, Boolean oneResponsePerUser, Instant closesAt,
                         Map<String, Object> settings) {
        SurveyInput input() {
            return new SurveyInput(title, description, status, SurveyDefinition.from(definition),
                    Boolean.TRUE.equals(anonymous), Boolean.TRUE.equals(oneResponsePerUser), closesAt,
                    SurveySettings.from(settings));
        }
    }

    /** @param invitation the token of the link the form was opened with ({@code ?invitation=}), if any */
    record ResponseRequest(@NotNull Map<String, Object> answers, @Size(max = 64) String invitation) {
    }

    record NoteRequest(@NotBlank @Size(max = 2000) String text) {
    }

    record NoteView(UUID id, String author, String text, Instant createdAt) {
    }

    /** "Try it" in the builder: the definition on screen and the answers typed into the preview. */
    record TryRequest(@NotNull Map<String, Object> definition, Map<String, Object> answers) {
    }

    record SurveyView(UUID id, long version, String title, String description, String status, boolean anonymous,
                      boolean oneResponsePerUser, Instant closesAt, String owner, Instant createdAt,
                      Instant updatedAt, String updatedBy, Instant publishedAt, SurveyDefinition definition,
                      SurveySettings settings, long responseCount, Map<String, Long> followUpCounts) {
    }

    record SurveyListItem(UUID id, String title, String description, String status, String owner,
                          Instant closesAt, Instant updatedAt, int questionCount, long responseCount,
                          boolean answered, boolean anonymous) {
    }

    record FormView(UUID id, String title, String description, String status, boolean anonymous,
                    boolean oneResponsePerUser, Instant closesAt, SurveyDefinition definition, boolean open,
                    String closedReason, boolean alreadyAnswered, boolean preview, String resultsVisibility,
                    boolean hasResults) {
    }

    record SubmittedView(UUID responseId, Instant submittedAt, String successMessage) {
    }

    record ResponseView(UUID id, long surveyVersion, String respondent, Instant submittedAt,
                        Map<String, Object> answers, Map<String, String> display, String status, String priority,
                        java.time.LocalDate followUpAt, String assignee, Map<String, Object> tracking, long notes,
                        String source, Instant updatedAt, String updatedBy) {
    }

    record TryView(boolean valid, List<FieldProblem> problems) {
    }

    static SurveyView survey(Survey survey, long responseCount, Map<String, Long> followUpCounts) {
        return new SurveyView(survey.getId(), survey.getVersion(), survey.getTitle(), survey.getDescription(),
                value(survey.getStatus()), survey.isAnonymous(), survey.isOneResponsePerUser(), survey.getClosesAt(),
                survey.getOwner(), survey.getCreatedAt(), survey.getUpdatedAt(), survey.getUpdatedBy(),
                survey.getPublishedAt(), survey.definition(), survey.settings(), responseCount, followUpCounts);
    }

    static SurveyListItem item(SurveyService.Listed listed) {
        Survey survey = listed.survey();
        return new SurveyListItem(survey.getId(), survey.getTitle(), survey.getDescription(),
                value(survey.getStatus()), survey.getOwner(), survey.getClosesAt(), survey.getUpdatedAt(),
                survey.definition().answerableFields().size(), listed.responses(), listed.answered(),
                survey.isAnonymous());
    }

    static FormView form(SurveyService.Form form) {
        Survey survey = form.survey();
        return new FormView(survey.getId(), survey.getTitle(), survey.getDescription(), value(survey.getStatus()),
                survey.isAnonymous(), survey.isOneResponsePerUser(), survey.getClosesAt(), form.definition(),
                form.open(), form.closedReason(), form.alreadyAnswered(), form.preview(), survey.settings().results(),
                form.definition().fields().stream().anyMatch(field -> Boolean.TRUE.equals(field.showInResults())));
    }

    static ResponseView response(SurveyService.ResponseRow row) {
        SurveyResponse response = row.response();
        return new ResponseView(response.getId(), response.getSurveyVersion(), response.getRespondent(),
                response.getSubmittedAt(), row.answers(), row.display(), value(response.getFollowStatus()),
                response.getPriority() == null ? null : value(response.getPriority()), response.getFollowUpAt(),
                response.getAssignee(), response.tracking(), row.notes(),
                response.getSourceType() == null ? null : response.getSourceType() + ":" + response.getSourceId(),
                response.getUpdatedAt(), response.getUpdatedBy());
    }

    static NoteView note(SurveyResponseNote note) {
        return new NoteView(note.getId(), note.getAuthor(), note.getText(), note.getCreatedAt());
    }
}
