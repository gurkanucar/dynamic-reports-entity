package com.gucardev.dynamicreportsentity.platform.survey.web;

import com.gucardev.dynamicreportsentity.platform.api.ApiException;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.api.ApiResponse;
import com.gucardev.dynamicreportsentity.platform.api.HttpSupport;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import com.gucardev.dynamicreportsentity.platform.survey.Survey;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyDefinition;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyResponse;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.etag;
import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.expectedVersion;
import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.paging;

/** Surveys (owners design them), their forms (users answer them) and their results. */
@RestController
@PreAuthorize("hasAuthority('PLATFORM_USER')")
class SurveyController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SurveyService surveys;

    SurveyController(SurveyService surveys) {
        this.surveys = surveys;
    }

    // ---- surveys --------------------------------------------------------------------------------------

    /**
     * {@code scope=mine} (default): surveys I own (admins: all); {@code open}: surveys I can answer now. Filters:
     * {@code status} (repeatable), {@code q} (title, description); {@code sort}: title, updatedAt, createdAt, status,
     * closesAt (default {@code -updatedAt}).
     */
    @GetMapping("/api/surveys")
    ApiResponse<List<SurveyApi.SurveyListItem>> list(@RequestParam(defaultValue = "mine") String scope,
                                                     @RequestParam(required = false) List<String> status,
                                                     @RequestParam(required = false) String q,
                                                     @RequestParam(required = false) String sort,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size,
                                                     Authentication authentication) {
        if (!scope.equals("mine") && !scope.equals("open")) {
            throw new ApiException(ApiException.Kind.BAD_REQUEST, "INVALID_PARAMETER", "scope must be mine or open.");
        }
        int[] paging = paging(page, size);
        List<Survey.Status> statuses = status == null ? List.of()
                : status.stream().map(value -> HttpSupport.parse(Survey.Status.class, value, "status")).toList();
        return ApiResponse.page(surveys.list(scope, statuses, q, sort, Actor.of(authentication), paging[0], paging[1]),
                SurveyApi::item);
    }

    @PostMapping("/api/surveys")
    ResponseEntity<ApiResponse<SurveyApi.SurveyView>> create(@Valid @RequestBody SurveyApi.SurveyRequest request,
                                                             Authentication authentication) {
        Survey survey = surveys.create(request.input(), Actor.of(authentication));
        return ResponseEntity.created(URI.create("/api/surveys/" + survey.getId())).eTag(etag(survey.getVersion()))
                .body(ApiResponse.of(SurveyApi.survey(survey, 0, Map.of())));
    }

    @GetMapping("/api/surveys/{id}")
    ResponseEntity<ApiResponse<SurveyApi.SurveyView>> get(@PathVariable long id, Authentication authentication) {
        Survey survey = surveys.get(id, Actor.of(authentication));
        return ResponseEntity.ok().eTag(etag(survey.getVersion()))
                .body(ApiResponse.of(SurveyApi.survey(survey, surveys.responseCount(id), surveys.statusCounts(id, Actor.of(authentication)))));
    }

    @PutMapping("/api/surveys/{id}")
    ResponseEntity<ApiResponse<SurveyApi.SurveyView>> update(@PathVariable long id,
                                                             @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                             @Valid @RequestBody SurveyApi.SurveyRequest request,
                                                             Authentication authentication) {
        long expected = expectedVersion(ifMatch);
        Survey survey = surveys.update(id, expected, request.input(), Actor.of(authentication));
        return ResponseEntity.ok().eTag(etag(survey.getVersion()))
                .body(ApiResponse.of(SurveyApi.survey(survey, surveys.responseCount(id), surveys.statusCounts(id, Actor.of(authentication)))));
    }

    @DeleteMapping("/api/surveys/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id, Authentication authentication) {
        surveys.delete(id, Actor.of(authentication));
    }

    /** Checks a definition and, optionally, answers to it, without saving: the builder's "try it". */
    @PostMapping("/api/survey-checks")
    ApiResponse<SurveyApi.TryView> check(@Valid @RequestBody SurveyApi.TryRequest request) {
        SurveyDefinition definition = SurveyDefinition.from(request.definition());
        List<FieldProblem> problems = request.answers() == null ? surveys.check(definition)
                : surveys.tryAnswers(definition, request.answers());
        return ApiResponse.of(new SurveyApi.TryView(problems.isEmpty(), problems));
    }

    // ---- answering ------------------------------------------------------------------------------------

    /** {@code invitation}: the token of a link a waiting flow step sent. */
    @GetMapping("/api/surveys/{id}/form")
    ApiResponse<SurveyApi.FormView> form(@PathVariable long id, @RequestParam(required = false) String invitation,
                                         Authentication authentication) {
        return ApiResponse.of(SurveyApi.form(surveys.form(id, Actor.of(authentication), invitation)));
    }

    /** The counted answers of the questions marked "show in results", if the survey shows them to this user. */
    @GetMapping("/api/surveys/{id}/results")
    ApiResponse<SurveyService.Summary> results(@PathVariable long id, Authentication authentication) {
        return ApiResponse.of(surveys.publicResults(id, Actor.of(authentication)));
    }

    @PostMapping("/api/surveys/{id}/responses")
    ResponseEntity<ApiResponse<SurveyApi.SubmittedView>> submit(@PathVariable long id,
                                                                @Valid @RequestBody SurveyApi.ResponseRequest request,
                                                                Authentication authentication) {
        SurveyResponse response = surveys.submit(id, request.answers(), Actor.of(authentication), request.invitation());
        String success = surveys.form(id, Actor.of(authentication), null).definition().successMessage();
        return ResponseEntity.created(URI.create("/api/surveys/" + id + "/responses/" + response.getId()))
                .body(ApiResponse.of(new SurveyApi.SubmittedView(response.getId(), response.getSubmittedAt(),
                        success)));
    }

    // ---- results --------------------------------------------------------------------------------------

    /**
     * The responses (owner or admin). Filters: {@code status} and {@code priority} (repeatable), {@code assignee},
     * {@code q} (respondent, answers, assignee), {@code submittedFrom}/{@code submittedTo} (days); {@code sort}:
     * submittedAt, status, priority, followUpAt, assignee, respondent (default {@code -submittedAt}).
     */
    @GetMapping("/api/surveys/{id}/responses")
    ApiResponse<List<SurveyApi.ResponseView>> responses(@PathVariable long id,
                                                        @RequestParam(required = false) List<String> status,
                                                        @RequestParam(required = false) List<String> priority,
                                                        @RequestParam(required = false) String assignee,
                                                        @RequestParam(required = false) String q,
                                                        @RequestParam(required = false) java.time.LocalDate submittedFrom,
                                                        @RequestParam(required = false) java.time.LocalDate submittedTo,
                                                        @RequestParam(required = false) String sort,
                                                        @RequestParam(required = false) Integer page,
                                                        @RequestParam(required = false) Integer size,
                                                        Authentication authentication) {
        int[] paging = paging(page, size);
        SurveyService.ResponseFilter filter = new SurveyService.ResponseFilter(
                status == null ? List.of() : status.stream().map(value -> HttpSupport.parse(
                        SurveyResponse.FollowStatus.class, value, "status")).toList(),
                priority == null ? List.of() : priority.stream().map(value -> HttpSupport.parse(
                        SurveyResponse.Priority.class, value, "priority")).toList(),
                assignee, q, submittedFrom, submittedTo, sort);
        return ApiResponse.page(surveys.responses(id, filter, Actor.of(authentication), paging[0], paging[1]),
                SurveyApi::response);
    }

    @GetMapping("/api/surveys/{id}/responses/{responseId}")
    ApiResponse<SurveyApi.ResponseView> response(@PathVariable long id, @PathVariable long responseId,
                                                 Authentication authentication) {
        return ApiResponse.of(SurveyApi.response(surveys.response(id, responseId, Actor.of(authentication))));
    }

    /**
     * The owner's follow-up of a response (JSON merge patch): {@code status} (new, in_progress, resolved,
     * unresolved), {@code priority} (low, normal, high), {@code followUpAt} (YYYY-MM-DD), {@code assignee},
     * {@code tracking} (extra field values by id). Absent fields stay, {@code null} clears. Answers never change.
     */
    @PatchMapping(value = "/api/surveys/{id}/responses/{responseId}",
            consumes = {"application/merge-patch+json", MediaType.APPLICATION_JSON_VALUE})
    ApiResponse<SurveyApi.ResponseView> followUp(@PathVariable long id, @PathVariable long responseId,
                                                 @RequestBody Map<String, Object> changes,
                                                 Authentication authentication) {
        return ApiResponse.of(SurveyApi.response(surveys.followUp(id, responseId,
                new SurveyService.FollowUp(changes), Actor.of(authentication))));
    }

    @GetMapping("/api/surveys/{id}/responses/{responseId}/notes")
    ApiResponse<List<SurveyApi.NoteView>> notes(@PathVariable long id, @PathVariable long responseId,
                                                Authentication authentication) {
        return ApiResponse.of(surveys.notes(id, responseId, Actor.of(authentication)).stream().map(SurveyApi::note).toList());
    }

    @PostMapping("/api/surveys/{id}/responses/{responseId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<SurveyApi.NoteView> addNote(@PathVariable long id, @PathVariable long responseId,
                                            @Valid @RequestBody SurveyApi.NoteRequest request,
                                            Authentication authentication) {
        return ApiResponse.of(SurveyApi.note(surveys.addNote(id, responseId, request.text(), Actor.of(authentication))));
    }

    @DeleteMapping("/api/surveys/{id}/responses/{responseId}/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteNote(@PathVariable long id, @PathVariable long responseId, @PathVariable long noteId,
                    Authentication authentication) {
        surveys.deleteNote(id, responseId, noteId, Actor.of(authentication));
    }

    @GetMapping("/api/surveys/{id}/summary")
    ApiResponse<SurveyService.Summary> summary(@PathVariable long id, Authentication authentication) {
        return ApiResponse.of(surveys.summary(id, Actor.of(authentication)));
    }

    /** All responses as an Excel file: one row per response, one column per question (answers as people read them). */
    @GetMapping("/api/surveys/{id}/responses/export")
    ResponseEntity<StreamingResponseBody> export(@PathVariable long id, Authentication authentication) {
        Actor viewer = Actor.of(authentication);
        Survey survey = surveys.get(id, viewer);   // 404 before the stream starts
        String fileName = "anket-" + id + "-yanitlar.xlsx";
        StreamingResponseBody body = out -> SurveyExport.write(survey, viewer, surveys, out);
        return ResponseEntity.ok().contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(body);
    }
}
