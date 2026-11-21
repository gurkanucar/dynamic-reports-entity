package com.gucardev.dynamicreportsentity.platform.survey.web;

import com.gucardev.dynamicreportsentity.platform.api.ApiException;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.api.ApiResponse;
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

    /** {@code scope=mine} (default): surveys I own (admins: all); {@code open}: surveys I can answer now. */
    @GetMapping("/api/surveys")
    ApiResponse<List<SurveyApi.SurveyListItem>> list(@RequestParam(defaultValue = "mine") String scope,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size,
                                                     Authentication authentication) {
        if (!scope.equals("mine") && !scope.equals("open")) {
            throw new ApiException(ApiException.Kind.BAD_REQUEST, "INVALID_PARAMETER", "scope must be mine or open.");
        }
        int[] paging = paging(page, size);
        return ApiResponse.page(surveys.list(scope, Actor.of(authentication), paging[0], paging[1]), SurveyApi::item);
    }

    @PostMapping("/api/surveys")
    ResponseEntity<ApiResponse<SurveyApi.SurveyView>> create(@Valid @RequestBody SurveyApi.SurveyRequest request,
                                                             Authentication authentication) {
        Survey survey = surveys.create(request.input(), Actor.of(authentication));
        return ResponseEntity.created(URI.create("/api/surveys/" + survey.getId())).eTag(etag(survey.getVersion()))
                .body(ApiResponse.of(SurveyApi.survey(survey, 0)));
    }

    @GetMapping("/api/surveys/{id}")
    ResponseEntity<ApiResponse<SurveyApi.SurveyView>> get(@PathVariable long id, Authentication authentication) {
        Survey survey = surveys.get(id, Actor.of(authentication));
        return ResponseEntity.ok().eTag(etag(survey.getVersion()))
                .body(ApiResponse.of(SurveyApi.survey(survey, surveys.responseCount(id))));
    }

    @PutMapping("/api/surveys/{id}")
    ResponseEntity<ApiResponse<SurveyApi.SurveyView>> update(@PathVariable long id,
                                                             @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                             @Valid @RequestBody SurveyApi.SurveyRequest request,
                                                             Authentication authentication) {
        long expected = expectedVersion(ifMatch);
        Survey survey = surveys.update(id, expected, request.input(), Actor.of(authentication));
        return ResponseEntity.ok().eTag(etag(survey.getVersion()))
                .body(ApiResponse.of(SurveyApi.survey(survey, surveys.responseCount(id))));
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

    @GetMapping("/api/surveys/{id}/form")
    ApiResponse<SurveyApi.FormView> form(@PathVariable long id, Authentication authentication) {
        return ApiResponse.of(SurveyApi.form(surveys.form(id, Actor.of(authentication))));
    }

    @PostMapping("/api/surveys/{id}/responses")
    ResponseEntity<ApiResponse<SurveyApi.SubmittedView>> submit(@PathVariable long id,
                                                                @Valid @RequestBody SurveyApi.ResponseRequest request,
                                                                Authentication authentication) {
        SurveyResponse response = surveys.submit(id, request.answers(), Actor.of(authentication));
        String success = surveys.form(id, Actor.of(authentication)).definition().successMessage();
        return ResponseEntity.created(URI.create("/api/surveys/" + id + "/responses/" + response.getId()))
                .body(ApiResponse.of(new SurveyApi.SubmittedView(response.getId(), response.getSubmittedAt(),
                        success)));
    }

    // ---- results --------------------------------------------------------------------------------------

    @GetMapping("/api/surveys/{id}/responses")
    ApiResponse<List<SurveyApi.ResponseView>> responses(@PathVariable long id,
                                                        @RequestParam(required = false) Integer page,
                                                        @RequestParam(required = false) Integer size,
                                                        Authentication authentication) {
        int[] paging = paging(page, size);
        return ApiResponse.page(surveys.responses(id, Actor.of(authentication), paging[0], paging[1]),
                SurveyApi::response);
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
