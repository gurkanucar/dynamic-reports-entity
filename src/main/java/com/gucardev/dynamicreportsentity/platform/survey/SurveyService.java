package com.gucardev.dynamicreportsentity.platform.survey;

import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.Kind;
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

    /** A response as the owner reads it. */
    public record ResponseRow(SurveyResponse response, Map<String, Object> answers, Map<String, String> display) {
    }

    private static final int MAX_SUMMARY_RESPONSES = 50_000;
    private static final int SAMPLES = 5;

    private final SurveyRepository surveys;
    private final SurveyResponseRepository responses;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    SurveyService(SurveyRepository surveys, SurveyResponseRepository responses, ApplicationEventPublisher events,
                  Clock clock) {
        this.surveys = surveys;
        this.responses = responses;
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
        PageRequest request = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Survey> found = switch (scope) {
            case "open" -> surveys.findOpen(clock.instant(), request);
            default -> admin(viewer) ? surveys.findAll(request) : surveys.findByOwner(viewer.username(), request);
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
        if (!problems.isEmpty()) {
            throw SurveyException.invalid(problems);
        }
    }

    // ---- answering ------------------------------------------------------------------------------------

    /** The form to fill in: a published survey for anyone, a draft only for its owner (to try it). */
    @Transactional(readOnly = true)
    public Form form(long id, Actor viewer) {
        Survey survey = surveys.findById(id).orElseThrow(() -> SurveyException.notFound(id));
        boolean manager = canManage(survey, viewer);
        if (survey.getStatus() == Survey.Status.DRAFT && !manager) {
            throw SurveyException.notFound(id);
        }
        Instant now = clock.instant();
        boolean answered = survey.isOneResponsePerUser()
                && !responses.answeredAmong(List.of(id), viewer.username(),
                List.of(respondentKey(id, viewer.username()))).isEmpty();
        String closedReason = switch (survey.getStatus()) {
            case DRAFT -> "draft";
            case CLOSED -> "closed";
            case PUBLISHED -> survey.open(now) ? (answered ? "answered" : null) : "deadline_passed";
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
        String key = survey.isOneResponsePerUser() ? respondentKey(id, viewer.username()) : null;
        SurveyResponse response;
        try {
            response = responses.saveAndFlush(new SurveyResponse(survey,
                    survey.isAnonymous() ? null : viewer.username(), key, outcome.answers(), now));
        } catch (DataIntegrityViolationException e) {
            throw new SurveyException(Kind.CONFLICT, "ALREADY_ANSWERED", "You have already answered this survey.");
        }
        events.publishEvent(new SurveyResponseSubmitted(survey.getId(), survey.getTitle(), survey.getOwner(),
                response.getId(), response.getRespondent(), outcome.answers(),
                SurveyAnswers.display(definition, outcome.answers()), labels(definition), now));
        return response;
    }

    // ---- results --------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<ResponseRow> responses(long id, Actor viewer, int page, int size) {
        Survey survey = get(id, viewer);
        SurveyDefinition definition = survey.definition();
        return responses.findBySurveyId(id, PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "submittedAt")))
                .map(response -> row(definition, response));
    }

    @Transactional(readOnly = true)
    public Summary summary(long id, Actor viewer) {
        Survey survey = get(id, viewer);
        List<Field> fields = survey.definition().answerableFields();
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
        each(id, response -> consumer.accept(row(definition, response)));
    }

    private void each(long id, Consumer<SurveyResponse> consumer) {
        try (Stream<SurveyResponse> stream = responses.streamBySurveyIdOrderById(id)) {
            stream.limit(MAX_SUMMARY_RESPONSES).forEach(consumer);
        }
    }

    private static ResponseRow row(SurveyDefinition definition, SurveyResponse response) {
        Map<String, Object> answers = response.answers();
        return new ResponseRow(response, answers, SurveyAnswers.display(definition, answers));
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
