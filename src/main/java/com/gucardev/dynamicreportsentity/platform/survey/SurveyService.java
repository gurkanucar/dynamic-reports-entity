package com.gucardev.dynamicreportsentity.platform.survey;

import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.Kind;
import com.gucardev.dynamicreportsentity.platform.api.ListQuery;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyDefinition.Field;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Surveys and their responses. The owner (or a platform admin) designs a survey and reads its responses; every
 * platform user may answer a published one. Answers are checked here ({@link SurveyAnswers}), whatever the browser
 * did.
 */
@Service
public class SurveyService {

    public static final String ADMIN_AUTHORITY = "PLATFORM_ADMIN";

    /** A survey with the number of its responses. */
    public record Listed(Survey survey, long responses, boolean answered) {
    }

    /** What a respondent sees: the form, and whether they can still send it. */
    public record Form(Survey survey, SurveyDefinition definition, boolean open, String closedReason,
                       boolean alreadyAnswered, boolean preview) {
    }

    /** Counts per question, for the owner's results page. */
    public record Summary(long total, List<FieldSummary> fields) {
    }

    /**
     * @param counts  choice, yes/no, rating and score questions: how often each answer was given
     * @param average rating, score and number questions
     * @param samples text questions: the latest answers
     */
    public record FieldSummary(String fieldId, String label, String type, long answered, List<Count> counts,
                               Double average, List<String> samples) {
    }

    public record Count(String key, String label, long count) {
    }

    /** A response as the owner reads it; {@code notes}: how many notes it has. */
    public record ResponseRow(SurveyResponse response, Map<String, Object> answers, Map<String, String> display,
                              long notes) {
    }

    /** Filters of the owner's response list; empty lists and nulls filter nothing. */
    public record ResponseFilter(List<SurveyResponse.FollowStatus> statuses, List<SurveyResponse.Priority> priorities,
                                 String assignee, String q, java.time.LocalDate submittedFrom,
                                 java.time.LocalDate submittedTo, String sort) {
        public static final ResponseFilter NONE = new ResponseFilter(List.of(), List.of(), null, null, null, null, null);
    }

    /**
     * A change of a response's follow-up; only the fields present change ({@code null} clears).
     *
     * @param tracking extra field values by id (replaces them all when present)
     */
    public record FollowUp(Map<String, Object> changes) {
    }

    /** Sortable fields (API name → property). */
    public static final Map<String, String> SURVEY_SORTS = Map.of("title", "title", "updatedAt", "updatedAt",
            "createdAt", "createdAt", "status", "status", "closesAt", "closesAt", "id", "id");
    public static final Map<String, String> RESPONSE_SORTS = Map.of("submittedAt", "submittedAt",
            "status", "followStatus", "priority", "priorityRank", "followUpAt", "followUpAt", "assignee", "assignee",
            "respondent", "respondent", "id", "id");

    /** Random tokens of invitation links. */
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    private static final int MAX_SUMMARY_RESPONSES = 50_000;
    private static final int SAMPLES = 5;

    private final SurveyRepository surveys;
    private final SurveyResponseRepository responses;
    private final SurveyResponseNoteRepository notes;
    private final SurveyInvitationRepository invitations;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    SurveyService(SurveyRepository surveys, SurveyResponseRepository responses, SurveyResponseNoteRepository notes,
                  SurveyInvitationRepository invitations, ApplicationEventPublisher events, Clock clock) {
        this.surveys = surveys;
        this.responses = responses;
        this.notes = notes;
        this.invitations = invitations;
        this.events = events;
        this.clock = clock;
    }

    // ---- designing ------------------------------------------------------------------------------------

    /** What is wrong with a definition (empty: it can be saved). Also used by the assistant. */
    public List<FieldProblem> check(SurveyDefinition definition) {
        return SurveyChecker.check(definition);
    }

    /** {@code scope=mine}: surveys I own (admins: every survey); {@code open}: surveys I can answer now. 1-based page. */
    @Transactional(readOnly = true)
    public Page<Listed> list(String scope, Actor viewer, int page, int size) {
        return list(scope, List.of(), null, null, viewer, page, size);
    }

    /**
     * @param statuses only these (mine; empty: all)
     * @param q        over title and description
     * @param sort     title, updatedAt, createdAt, status, closesAt; default {@code -updatedAt}
     */
    @Transactional(readOnly = true)
    public Page<Listed> list(String scope, List<Survey.Status> statuses, String q, String sort, Actor viewer, int page,
                             int size) {
        PageRequest request = ListQuery.page(page, size, ListQuery.sort(sort, SURVEY_SORTS, "-updatedAt"));
        String like = ListQuery.like(q);
        Page<Survey> found = switch (scope) {
            case "open" -> surveys.findOpen(clock.instant(), like, request);
            default -> surveys.search(admin(viewer) ? null : viewer.username(),
                    statuses.isEmpty() ? List.of(Survey.Status.values()) : statuses, like, request);
        };
        List<Long> ids = found.getContent().stream().map(Survey::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        Set<Long> answered = Set.of();
        if (!ids.isEmpty()) {
            responses.countBySurveyIds(ids).forEach(row -> counts.put((Long) row[0], (Long) row[1]));
            answered = Set.copyOf(responses.answeredAmong(ids, viewer.username(),
                    ids.stream().map(id -> respondentKey(id, viewer.username())).toList()));
        }
        Set<Long> mine = answered;
        return found.map(survey -> new Listed(survey, counts.getOrDefault(survey.getId(), 0L),
                mine.contains(survey.getId())));
    }

    /** A survey its owner (or an admin) manages; others get 404. */
    @Transactional(readOnly = true)
    public Survey get(long id, Actor viewer) {
        Survey survey = surveys.findById(id).orElseThrow(() -> SurveyException.notFound(id));
        if (!canManage(survey, viewer)) {
            throw SurveyException.notFound(id);
        }
        return survey;
    }

    public long responseCount(long surveyId) {
        return responses.countBySurveyId(surveyId);
    }

    @Transactional
    public Survey create(SurveyInput input, Actor viewer) {
        validate(input);
        Instant now = clock.instant();
        Survey survey = new Survey(viewer.username(), now);
        survey.define(input, viewer.username(), now);
        return surveys.saveAndFlush(survey);
    }

    @Transactional
    public Survey update(long id, long expectedVersion, SurveyInput input, Actor viewer) {
        Survey survey = get(id, viewer);
        if (survey.getVersion() != expectedVersion) {
            throw versionMismatch();
        }
        validate(input);
        survey.define(input, viewer.username(), clock.instant());
        try {
            return surveys.saveAndFlush(survey);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw versionMismatch();
        }
    }

    /** Deletes the survey and its responses. */
    @Transactional
    public void delete(long id, Actor viewer) {
        Survey survey = get(id, viewer);
        notes.deleteBySurveyId(survey.getId());
        responses.deleteBySurveyId(survey.getId());
        surveys.delete(survey);
    }

    private void validate(SurveyInput input) {
        List<FieldProblem> problems = new ArrayList<>(check(input.definition()));
        if (input.title() == null || input.title().isBlank()) {
            problems.addFirst(new FieldProblem("title", "REQUIRED", "The survey needs a title."));
        }
        if (input.status() == null) {
            problems.add(new FieldProblem("status", "REQUIRED", "status is draft, published or closed."));
        }
        problems.addAll(SurveyChecker.checkSettings(input.settings(), input.anonymous(), input.oneResponsePerUser()));
        if (!problems.isEmpty()) {
            throw SurveyException.invalid(problems);
        }
    }

    // ---- answering ------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Form form(long id, Actor viewer) {
        return form(id, viewer, null);
    }

    /**
     * The form to fill in: a published survey for anyone, a draft only for its owner (to try it). Through an
     * invitation ({@code invitation}: its token) the "one response per user" limit does not apply: a flow may ask
     * the same person again, e.g. for a second request.
     */
    @Transactional(readOnly = true)
    public Form form(long id, Actor viewer, String invitation) {
        Survey survey = surveys.findById(id).orElseThrow(() -> SurveyException.notFound(id));
        boolean manager = canManage(survey, viewer);
        if (survey.getStatus() == Survey.Status.DRAFT && !manager) {
            throw SurveyException.notFound(id);
        }
        Instant now = clock.instant();
        String invitationProblem = null;
        if (invitation != null) {
            SurveyInvitation found = invitations.findById(invitation).filter(item -> item.getSurveyId() == id).orElse(null);
            invitationProblem = found == null ? "invitation_invalid"
                    : found.getResponseId() != null ? "invitation_used" : !found.usable(now) ? "invitation_expired" : null;
        }
        boolean answered = invitation == null && survey.isOneResponsePerUser()
                && !responses.answeredAmong(List.of(id), viewer.username(),
                List.of(respondentKey(id, viewer.username()))).isEmpty();
        String closedReason = switch (survey.getStatus()) {
            case DRAFT -> "draft";
            case CLOSED -> "closed";
            case PUBLISHED -> !survey.open(now) ? "deadline_passed" : invitationProblem != null ? invitationProblem
                    : answered ? "answered" : null;
        };
        SurveyDefinition definition = survey.definition();
        return new Form(survey, manager ? definition : definition.forRespondent(), closedReason == null,
                closedReason, answered, survey.getStatus() == Survey.Status.DRAFT);
    }

    /** Checks answers against a definition without saving anything: the builder's "try it". */
    public List<FieldProblem> tryAnswers(SurveyDefinition definition, Map<String, Object> answers) {
        List<FieldProblem> problems = check(definition);
        return problems.isEmpty() ? SurveyAnswers.check(definition, answers).problems() : problems;
    }

    @Transactional
    public SurveyResponse submit(long id, Map<String, Object> answers, Actor viewer) {
        return submit(id, answers, viewer, null);
    }

    /** @param invitation the token of the link the response came through (a waiting flow step); null otherwise */
    @Transactional
    public SurveyResponse submit(long id, Map<String, Object> answers, Actor viewer, String invitation) {
        Survey survey = surveys.findById(id).orElseThrow(() -> SurveyException.notFound(id));
        Instant now = clock.instant();
        if (survey.getStatus() == Survey.Status.DRAFT && !canManage(survey, viewer)) {
            throw SurveyException.notFound(id);
        }
        if (!survey.open(now)) {
            throw new SurveyException(Kind.CONFLICT, "SURVEY_CLOSED", survey.getStatus() == Survey.Status.DRAFT
                    ? "The survey is a draft: publish it to take responses."
                    : "The survey takes no more responses.");
        }
        SurveyDefinition definition = survey.definition();
        SurveyAnswers.Outcome outcome = SurveyAnswers.check(definition, answers);
        if (!outcome.valid()) {
            throw SurveyException.invalid(outcome.problems());
        }
        SurveyInvitation used = null;
        if (invitation != null) {
            used = invitations.findById(invitation).filter(item -> item.getSurveyId() == id).orElse(null);
            if (used == null || !used.usable(now)) {
                throw new SurveyException(Kind.CONFLICT, "INVITATION_NOT_USABLE",
                        "This link was already used, has expired or is no longer needed.");
            }
        }
        // an invitation's response does not count against "one response per user"
        String key = survey.isOneResponsePerUser() && used == null ? respondentKey(id, viewer.username()) : null;
        SurveyResponse response = new SurveyResponse(survey, survey.isAnonymous() ? null : viewer.username(), key,
                outcome.answers(), now);
        if (used != null) {
            response.cameThrough(used);
        }
        try {
            response = responses.saveAndFlush(response);
        } catch (DataIntegrityViolationException e) {
            throw new SurveyException(Kind.CONFLICT, "ALREADY_ANSWERED", "You have already answered this survey.");
        }
        if (used != null) {
            used.use(response.getId());
            invitations.save(used);
        }
        events.publishEvent(new SurveyResponseSubmitted(survey.getId(), survey.getTitle(), survey.getOwner(),
                response.getId(), response.getRespondent(), outcome.answers(),
                SurveyAnswers.display(definition, outcome.answers()), labels(definition), now,
                response.getSourceType(), response.getSourceId()));
        return response;
    }

    // ---- results --------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<ResponseRow> responses(long id, Actor viewer, int page, int size) {
        return responses(id, ResponseFilter.NONE, viewer, page, size);
    }

    @Transactional(readOnly = true)
    public Page<ResponseRow> responses(long id, ResponseFilter filter, Actor viewer, int page, int size) {
        Survey survey = get(id, viewer);
        SurveyDefinition definition = survey.definition();
        ListQuery.range(filter.submittedFrom(), filter.submittedTo(), "submitted");
        Page<SurveyResponse> found = responses.search(id,
                filter.statuses().isEmpty() ? List.of(SurveyResponse.FollowStatus.values()) : filter.statuses(),
                filter.priorities().isEmpty(), filter.priorities().isEmpty() ? List.of(SurveyResponse.Priority.NORMAL)
                        : filter.priorities(),
                filter.assignee() == null || filter.assignee().isBlank() ? null : filter.assignee().strip(),
                ListQuery.like(filter.q()), ListQuery.from(filter.submittedFrom()), ListQuery.to(filter.submittedTo()),
                ListQuery.page(page, size, ListQuery.sort(filter.sort(), RESPONSE_SORTS, "-submittedAt")));
        Map<Long, Long> noteCounts = new HashMap<>();
        List<Long> ids = found.getContent().stream().map(SurveyResponse::getId).toList();
        if (!ids.isEmpty()) {
            notes.countByResponseIds(ids).forEach(row -> noteCounts.put((Long) row[0], (Long) row[1]));
        }
        return found.map(response -> row(definition, response, noteCounts.getOrDefault(response.getId(), 0L)));
    }

    /** How many responses are in each follow-up status (for the filter chips). */
    @Transactional(readOnly = true)
    public Map<String, Long> statusCounts(long id, Actor viewer) {
        get(id, viewer);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (SurveyResponse.FollowStatus status : SurveyResponse.FollowStatus.values()) {
            counts.put(status.name().toLowerCase(java.util.Locale.ROOT), 0L);
        }
        responses.countByStatus(id).forEach(row -> counts.put(
                ((SurveyResponse.FollowStatus) row[0]).name().toLowerCase(java.util.Locale.ROOT), (Long) row[1]));
        return counts;
    }

    /** One response with its notes. */
    @Transactional(readOnly = true)
    public ResponseRow response(long id, long responseId, Actor viewer) {
        Survey survey = get(id, viewer);
        SurveyResponse response = findResponse(id, responseId);
        return row(survey.definition(), response, notes.findByResponseIdOrderById(responseId).size());
    }

    @Transactional(readOnly = true)
    public List<SurveyResponseNote> notes(long id, long responseId, Actor viewer) {
        get(id, viewer);
        findResponse(id, responseId);
        return notes.findByResponseIdOrderById(responseId);
    }

    /** Changes the follow-up of a response; checks values against the survey's tracking settings. */
    @Transactional
    public ResponseRow followUp(long id, long responseId, FollowUp change, Actor viewer) {
        Survey survey = get(id, viewer);
        SurveyResponse response = findResponse(id, responseId);
        SurveySettings.Tracking tracking = survey.settings().tracking();
        Map<String, Object> changes = change.changes();
        List<FieldProblem> problems = new ArrayList<>();
        SurveyResponse.FollowStatus status = response.getFollowStatus();
        if (changes.containsKey("status")) {
            status = parse(SurveyResponse.FollowStatus.class, changes.get("status"), "status", problems, false);
        }
        SurveyResponse.Priority priority = response.getPriority();
        if (changes.containsKey("priority")) {
            priority = parse(SurveyResponse.Priority.class, changes.get("priority"), "priority", problems, true);
        }
        java.time.LocalDate followUpAt = response.getFollowUpAt();
        if (changes.containsKey("followUpAt")) {
            Object value = changes.get("followUpAt");
            try {
                followUpAt = value == null || value.toString().isBlank() ? null : java.time.LocalDate.parse(value.toString());
            } catch (java.time.format.DateTimeParseException e) {
                problems.add(new FieldProblem("followUpAt", "INVALID_DATE", "followUpAt is YYYY-MM-DD."));
            }
        }
        String assignee = response.getAssignee();
        if (changes.containsKey("assignee")) {
            Object value = changes.get("assignee");
            assignee = value == null || value.toString().isBlank() ? null : value.toString().strip();
            if (assignee != null && assignee.length() > 100) {
                problems.add(new FieldProblem("assignee", "INVALID_LENGTH", "At most 100 characters."));
            }
        }
        Map<String, Object> values = response.tracking();
        if (changes.containsKey("tracking")) {
            values = trackingValues(tracking, changes.get("tracking"), problems);
        }
        if (!problems.isEmpty()) {
            throw SurveyException.invalid(problems);
        }
        response.followUp(status, priority, followUpAt, assignee, values, viewer.username(), clock.instant());
        responses.save(response);
        return row(survey.definition(), response, notes.findByResponseIdOrderById(responseId).size());
    }

    @Transactional
    public SurveyResponseNote addNote(long id, long responseId, String text, Actor viewer) {
        get(id, viewer);
        SurveyResponse response = findResponse(id, responseId);
        if (text == null || text.isBlank() || text.length() > 2000) {
            throw SurveyException.invalid(List.of(new FieldProblem("text", "INVALID_LENGTH", "A note is 1-2000 characters.")));
        }
        return notes.save(new SurveyResponseNote(response, viewer.username(), text.strip(), clock.instant()));
    }

    /** Its author or the survey's owner (or an admin) removes a note. */
    @Transactional
    public void deleteNote(long id, long responseId, long noteId, Actor viewer) {
        get(id, viewer);
        SurveyResponseNote note = notes.findById(noteId).filter(item -> item.getResponseId() == responseId)
                .orElseThrow(() -> new SurveyException(Kind.NOT_FOUND, "NOTE_NOT_FOUND", "No note " + noteId + "."));
        notes.delete(note);
    }

    private SurveyResponse findResponse(long id, long responseId) {
        return responses.findById(responseId).filter(response -> response.getSurveyId() == id)
                .orElseThrow(() -> new SurveyException(Kind.NOT_FOUND, "RESPONSE_NOT_FOUND",
                        "Survey " + id + " has no response " + responseId + "."));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, Object value, String field, List<FieldProblem> problems,
                                               boolean nullable) {
        if (value == null || value.toString().isBlank()) {
            if (!nullable) {
                problems.add(new FieldProblem(field, "REQUIRED", field + " cannot be empty."));
            }
            return null;
        }
        try {
            return Enum.valueOf(type, value.toString().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            problems.add(new FieldProblem(field, "INVALID_VALUE", "Unknown " + field + " '" + value + "'."));
            return null;
        }
    }

    /** The extra tracking values, checked against their fields: text, a choice's option id, a date, a number. */
    private static Map<String, Object> trackingValues(SurveySettings.Tracking tracking, Object given,
                                                      List<FieldProblem> problems) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (!(given instanceof Map<?, ?> map)) {
            if (given != null) {
                problems.add(new FieldProblem("tracking", "INVALID_VALUE", "tracking is an object by field id."));
            }
            return values;
        }
        for (SurveySettings.TrackingField field : tracking.fields()) {
            Object value = map.get(field.id());
            if (value == null || value.toString().isBlank()) {
                continue;
            }
            String path = "tracking." + field.id();
            String text = value.toString().strip();
            switch (field.type()) {
                case "choice" -> {
                    if (field.options() == null || field.options().stream().noneMatch(option -> option.id().equals(text))) {
                        problems.add(new FieldProblem(path, "UNKNOWN_OPTION", "Not one of the options."));
                    } else {
                        values.put(field.id(), text);
                    }
                }
                case "date" -> {
                    try {
                        values.put(field.id(), java.time.LocalDate.parse(text).toString());
                    } catch (java.time.format.DateTimeParseException e) {
                        problems.add(new FieldProblem(path, "INVALID_DATE", "A date is YYYY-MM-DD."));
                    }
                }
                case "number" -> {
                    try {
                        values.put(field.id(), new java.math.BigDecimal(text.replace(',', '.')));
                    } catch (NumberFormatException e) {
                        problems.add(new FieldProblem(path, "INVALID_NUMBER", "Not a number."));
                    }
                }
                default -> {
                    if (text.length() > 500) {
                        problems.add(new FieldProblem(path, "INVALID_LENGTH", "At most 500 characters."));
                    } else {
                        values.put(field.id(), text);
                    }
                }
            }
        }
        return values;
    }

    // ---- results for respondents -----------------------------------------------------------------------

    /**
     * The counted answers of the questions marked {@code showInResults}, when the survey shows them to this user:
     * {@code everyone} who can open it, or {@code respondents} who have answered. Text answers are never included.
     */
    @Transactional(readOnly = true)
    public Summary publicResults(long id, Actor viewer) {
        Survey survey = surveys.findById(id).orElseThrow(() -> SurveyException.notFound(id));
        if (survey.getStatus() == Survey.Status.DRAFT && !canManage(survey, viewer)) {
            throw SurveyException.notFound(id);
        }
        String visibility = survey.settings().results();
        boolean allowed = canManage(survey, viewer) || visibility.equals("everyone")
                || visibility.equals("respondents") && !responses.answeredAmong(List.of(id), viewer.username(),
                List.of(respondentKey(id, viewer.username()))).isEmpty();
        if (!allowed) {
            throw new SurveyException(Kind.FORBIDDEN, "RESULTS_NOT_VISIBLE", visibility.equals("respondents")
                    ? "The results are shown after you answer." : "Only the owner sees the results of this survey.");
        }
        List<Field> shown = survey.definition().answerableFields().stream()
                .filter(field -> Boolean.TRUE.equals(field.showInResults())).toList();
        Summary summary = summarize(survey, shown);
        return new Summary(summary.total(), summary.fields().stream().map(field -> new FieldSummary(field.fieldId(),
                field.label(), field.type(), field.answered(), field.counts(), field.average(), List.of())).toList());
    }

    // ---- invitations (a flow step waits for a response) -----------------------------------------------

    public boolean exists(long id) {
        return surveys.existsById(id);
    }

    /** The survey's title for a waiting step's mail; empty if it does not exist. */
    @Transactional(readOnly = true)
    public java.util.Optional<String> title(long id) {
        return surveys.findById(id).map(Survey::getTitle);
    }

    /**
     * A single-use link to a published survey for something that waits (e.g. {@code flow-step} 42); asking again for
     * the same source returns the same link. The response is reported with that source
     * ({@link SurveyResponseSubmitted#sourceType()}).
     *
     * @return the token; the form is {@code /s/<id>?invitation=<token>}
     */
    @Transactional
    public String invite(long id, String sourceType, String sourceId, Instant expiresAt) {
        Survey survey = surveys.findById(id).orElseThrow(() -> SurveyException.notFound(id));
        if (survey.getStatus() != Survey.Status.PUBLISHED) {
            throw new SurveyException(Kind.CONFLICT, "SURVEY_CLOSED", "Survey " + id + " is not published.");
        }
        return invitations.findBySourceTypeAndSourceId(sourceType, sourceId).map(SurveyInvitation::getToken)
                .orElseGet(() -> {
                    byte[] bytes = new byte[24];
                    RANDOM.nextBytes(bytes);
                    String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                    invitations.save(new SurveyInvitation(token, id, sourceType, sourceId, clock.instant(), expiresAt));
                    return token;
                });
    }

    /** The waiting ended (timeout, cancelled): the link stops working. */
    @Transactional
    public void revoke(String sourceType, String sourceId) {
        invitations.findBySourceTypeAndSourceId(sourceType, sourceId).ifPresent(invitation -> {
            invitation.revoke();
            invitations.save(invitation);
        });
    }

    @Transactional(readOnly = true)
    public Summary summary(long id, Actor viewer) {
        Survey survey = get(id, viewer);
        return summarize(survey, survey.definition().answerableFields());
    }

    private Summary summarize(Survey survey, List<Field> fields) {
        long id = survey.getId();
        Map<String, Map<String, Long>> counts = new HashMap<>();
        Map<String, double[]> sums = new HashMap<>();   // {sum, n}
        Map<String, List<String>> samples = new HashMap<>();
        Map<String, Long> answered = new HashMap<>();
        long[] total = {0};
        each(id, response -> {
            total[0]++;
            Map<String, Object> answers = response.answers();
            for (Field field : fields) {
                Object value = answers.get(field.id());
                if (value == null) {
                    continue;
                }
                answered.merge(field.id(), 1L, Long::sum);
                Map<String, Long> fieldCounts = counts.computeIfAbsent(field.id(), key -> new LinkedHashMap<>());
                switch (field.type()) {
                    case "singleChoice", "dropdown", "yesNo", "rating", "nps" ->
                            fieldCounts.merge(String.valueOf(value), 1L, Long::sum);
                    case "multipleChoice" -> {
                        if (value instanceof List<?> list) {
                            list.forEach(item -> fieldCounts.merge(String.valueOf(item), 1L, Long::sum));
                        }
                    }
                    default -> {
                        // text, numbers and dates are not counted per value
                    }
                }
                if (value instanceof Number number && Set.of("rating", "nps", "number").contains(field.type())) {
                    double[] sum = sums.computeIfAbsent(field.id(), key -> new double[2]);
                    sum[0] += number.doubleValue();
                    sum[1]++;
                }
                if (Set.of("shortText", "longText", "email", "phone", "date").contains(field.type())) {
                    List<String> latest = samples.computeIfAbsent(field.id(), key -> new ArrayList<>());
                    latest.addFirst(String.valueOf(value));
                    if (latest.size() > SAMPLES) {
                        latest.removeLast();
                    }
                }
            }
        });
        List<FieldSummary> summaries = fields.stream().map(field -> new FieldSummary(field.id(), field.label(),
                field.type(), answered.getOrDefault(field.id(), 0L),
                countsOf(field, counts.getOrDefault(field.id(), Map.of())),
                sums.containsKey(field.id()) ? sums.get(field.id())[0] / sums.get(field.id())[1] : null,
                samples.getOrDefault(field.id(), List.of()))).toList();
        return new Summary(total[0], summaries);
    }

    /** Every possible answer in its natural order, also the ones nobody gave (0). */
    private static List<Count> countsOf(Field field, Map<String, Long> counts) {
        List<Count> result = new ArrayList<>();
        switch (field.type()) {
            case "singleChoice", "dropdown", "multipleChoice" -> field.options().forEach(option ->
                    result.add(new Count(option.id(), option.label(), counts.getOrDefault(option.id(), 0L))));
            case "yesNo" -> {
                result.add(new Count("true", "Evet", counts.getOrDefault("true", 0L)));
                result.add(new Count("false", "Hayır", counts.getOrDefault("false", 0L)));
            }
            case "rating" -> {
                for (int stars = 1; stars <= field.stars(); stars++) {
                    result.add(new Count(Integer.toString(stars), stars + " ★", counts.getOrDefault(Integer.toString(stars), 0L)));
                }
            }
            case "nps" -> {
                for (int score = 0; score <= 10; score++) {
                    result.add(new Count(Integer.toString(score), Integer.toString(score),
                            counts.getOrDefault(Integer.toString(score), 0L)));
                }
            }
            default -> {
                // no counts
            }
        }
        return result;
    }

    /** Every response, oldest first, for exports. */
    @Transactional(readOnly = true)
    public void forEachResponse(long id, Actor viewer, Consumer<ResponseRow> consumer) {
        SurveyDefinition definition = get(id, viewer).definition();
        each(id, response -> consumer.accept(row(definition, response, 0)));
    }

    private void each(long id, Consumer<SurveyResponse> consumer) {
        try (Stream<SurveyResponse> stream = responses.streamBySurveyIdOrderById(id)) {
            stream.limit(MAX_SUMMARY_RESPONSES).forEach(consumer);
        }
    }

    private static ResponseRow row(SurveyDefinition definition, SurveyResponse response, long noteCount) {
        Map<String, Object> answers = response.answers();
        return new ResponseRow(response, answers, SurveyAnswers.display(definition, answers), noteCount);
    }

    // ---- helpers --------------------------------------------------------------------------------------

    public boolean canManage(Survey survey, Actor viewer) {
        return survey.getOwner().equals(viewer.username()) || admin(viewer);
    }

    private static boolean admin(Actor viewer) {
        return viewer.hasAuthority(ADMIN_AUTHORITY);
    }

    private static Map<String, String> labels(SurveyDefinition definition) {
        return definition.answerableFields().stream()
                .collect(Collectors.toMap(Field::id, Field::label, (a, b) -> a, LinkedHashMap::new));
    }

    /** Tells one user's responses apart without naming them. */
    static String respondentKey(long surveyId, String username) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((surveyId + ":" + username).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SurveyException versionMismatch() {
        return new SurveyException(Kind.PRECONDITION_FAILED, "VERSION_MISMATCH",
                "The survey was changed in the meantime; reload it and try again.");
    }
}
