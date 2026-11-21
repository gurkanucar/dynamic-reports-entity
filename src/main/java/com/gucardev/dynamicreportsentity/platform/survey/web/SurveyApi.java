package com.gucardev.dynamicreportsentity.platform.survey.web;

import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.survey.Survey;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyDefinition;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyInput;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyResponse;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyService;
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
                         Boolean anonymous, Boolean oneResponsePerUser, Instant closesAt) {
        SurveyInput input() {
            return new SurveyInput(title, description, status, SurveyDefinition.from(definition),
                    Boolean.TRUE.equals(anonymous), Boolean.TRUE.equals(oneResponsePerUser), closesAt);
        }
    }

    record ResponseRequest(@NotNull Map<String, Object> answers) {
    }

    /** "Try it" in the builder: the definition on screen and the answers typed into the preview. */
    record TryRequest(@NotNull Map<String, Object> definition, Map<String, Object> answers) {
    }

    record SurveyView(long id, long version, String title, String description, String status, boolean anonymous,
                      boolean oneResponsePerUser, Instant closesAt, String owner, Instant createdAt,
                      Instant updatedAt, String updatedBy, Instant publishedAt, SurveyDefinition definition,
                      long responseCount) {
    }

    record SurveyListItem(long id, String title, String description, String status, String owner,
                          Instant closesAt, Instant updatedAt, int questionCount, long responseCount,
                          boolean answered, boolean anonymous) {
    }

    record FormView(long id, String title, String description, String status, boolean anonymous,
                    boolean oneResponsePerUser, Instant closesAt, SurveyDefinition definition, boolean open,
                    String closedReason, boolean alreadyAnswered, boolean preview) {
    }

    record SubmittedView(long responseId, Instant submittedAt, String successMessage) {
    }

    record ResponseView(long id, long surveyVersion, String respondent, Instant submittedAt,
                        Map<String, Object> answers, Map<String, String> display) {
    }

    record TryView(boolean valid, List<FieldProblem> problems) {
    }

    static SurveyView survey(Survey survey, long responseCount) {
        return new SurveyView(survey.getId(), survey.getVersion(), survey.getTitle(), survey.getDescription(),
                value(survey.getStatus()), survey.isAnonymous(), survey.isOneResponsePerUser(), survey.getClosesAt(),
                survey.getOwner(), survey.getCreatedAt(), survey.getUpdatedAt(), survey.getUpdatedBy(),
                survey.getPublishedAt(), survey.definition(), responseCount);
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
                form.open(), form.closedReason(), form.alreadyAnswered(), form.preview());
    }

    static ResponseView response(SurveyService.ResponseRow row) {
        SurveyResponse response = row.response();
        return new ResponseView(response.getId(), response.getSurveyVersion(), response.getRespondent(),
                response.getSubmittedAt(), row.answers(), row.display());
    }
}
