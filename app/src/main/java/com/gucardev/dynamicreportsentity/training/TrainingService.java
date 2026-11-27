package com.gucardev.dynamicreportsentity.training;

import com.gucardev.dynamicreportsentity.personnel.PersonnelRemoved;
import com.gucardev.dynamicreportsentity.personnel.PersonnelService;
import com.gucardev.dynamicreportsentity.platform.api.Admin;
import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.api.ListQuery;
import org.springframework.data.domain.Page;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The course catalog and who completed what, with renewal dates. */
@Service
public class TrainingService {

    public static final List<String> RECORD_STATUSES = List.of("PASSED", "FAILED", "IN_PROGRESS");

    public record TrainingInput(String name, String category, Integer validityMonths, boolean mandatory,
                                String internalNotes) {
    }

    /** @param nextRenewalDate null: completedOn + the training's validity (none if it never expires) */
    public record RecordInput(Long personnelId, Long trainingId, LocalDate completedOn, LocalDate nextRenewalDate,
                              Integer score, String status) {
    }

    /** @param due {@code overdue} (renewal passed) or {@code soon} (within 60 days) */
    public record RecordFilter(Long personnelId, Long trainingId, List<String> statuses, String due,
                               LocalDate completedFrom, LocalDate completedTo, String sort) {
    }

    public record Listed(Training training, long records, long overdue) {
    }

    public record ListedRecord(TrainingRecord record, String personnelName, String trainingName) {
    }

    private static final Map<String, String> SORTS = Map.of("name", "name", "category", "category",
            "validityMonths", "validityMonths", "mandatory", "mandatory", "id", "id");
    private static final Map<String, String> RECORD_SORTS = Map.of("completedOn", "completedOn",
            "nextRenewalDate", "nextRenewalDate", "score", "score", "status", "status", "id", "id");

    private final TrainingRepository trainings;
    private final TrainingRecordRepository records;
    private final PersonnelService personnel;
    private final Clock clock;

    TrainingService(TrainingRepository trainings, TrainingRecordRepository records, PersonnelService personnel,
                    Clock clock) {
        this.trainings = trainings;
        this.records = records;
        this.personnel = personnel;
        this.clock = clock;
    }

    // ---- catalog ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<Listed> list(String q, String category, Boolean mandatory, String sort, int page, int size) {
        Page<Training> found = trainings.search(ListQuery.like(q), Admin.text(category), mandatory,
                ListQuery.page(page, size, ListQuery.sort(sort, SORTS, "name")));
        Map<Long, long[]> summary = new HashMap<>();
        List<Long> ids = found.getContent().stream().map(Training::getId).toList();
        if (!ids.isEmpty()) {
            records.summarize(ids, LocalDate.now(clock)).forEach(row -> summary.put((Long) row[0],
                    new long[]{(Long) row[1], row[2] == null ? 0 : ((Number) row[2]).longValue()}));
        }
        return found.map(training -> {
            long[] values = summary.getOrDefault(training.getId(), new long[2]);
            return new Listed(training, values[0], values[1]);
        });
    }

    public List<Training> all() {
        return trainings.findAllByOrderByName();
    }

    public List<String> categories() {
        return trainings.categories();
    }

    @Transactional
    public Training create(TrainingInput input) {
        validate(input);
        Training training = new Training(input.name().strip());
        apply(training, input);
        return trainings.saveAndFlush(training);
    }

    @Transactional
    public Training update(long id, long expectedVersion, TrainingInput input) {
        Training training = find(id);
        Admin.requireVersion(training.getVersion(), expectedVersion);
        validate(input);
        apply(training, input);
        try {
            return trainings.saveAndFlush(training);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw Admin.versionMismatch();
        }
    }

    /** Only a training nobody has a record of. */
    @Transactional
    public void delete(long id) {
        Training training = find(id);
        long count = records.countByTrainingId(id);
        if (count > 0) {
            throw Admin.inUse("TRAINING_IN_USE", "The training", List.of(count + " eğitim kaydı"));
        }
        trainings.delete(training);
    }

    // ---- records ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<ListedRecord> records(RecordFilter filter, int page, int size) {
        ListQuery.range(filter.completedFrom(), filter.completedTo(), "completed");
        LocalDate today = LocalDate.now(clock);
        LocalDate renewalFrom = "soon".equals(filter.due()) ? today : null;
        LocalDate renewalTo = "overdue".equals(filter.due()) ? today.minusDays(1)
                : "soon".equals(filter.due()) ? today.plusDays(60) : null;
        Page<TrainingRecord> found = records.search(filter.personnelId(), filter.trainingId(),
                filter.statuses().isEmpty(), filter.statuses().isEmpty() ? List.of("-") : filter.statuses(),
                renewalFrom, renewalTo, filter.completedFrom(), filter.completedTo(),
                ListQuery.page(page, size, ListQuery.sort(filter.sort(), RECORD_SORTS, "nextRenewalDate")));
        Map<Long, String> people = personnel.names(found.getContent().stream().map(TrainingRecord::getPersonnelId).toList());
        Map<Long, String> names = new HashMap<>();
        trainings.findAllById(found.getContent().stream().map(TrainingRecord::getTrainingId).distinct().toList())
                .forEach(training -> names.put(training.getId(), training.getName()));
        return found.map(record -> new ListedRecord(record, people.get(record.getPersonnelId()),
                names.get(record.getTrainingId())));
    }

    @Transactional
    public TrainingRecord createRecord(RecordInput input) {
        Training training = validate(input);
        TrainingRecord record = new TrainingRecord(input.personnelId(), input.trainingId());
        record.define(input.personnelId(), input.trainingId(), input.completedOn(), renewal(input, training),
                input.score(), input.status());
        return records.saveAndFlush(record);
    }

    @Transactional
    public TrainingRecord updateRecord(long id, long expectedVersion, RecordInput input) {
        TrainingRecord record = records.findById(id).orElseThrow(Admin.notFound("TRAINING_RECORD_NOT_FOUND", "record", id));
        Admin.requireVersion(record.getVersion(), expectedVersion);
        Training training = validate(input);
        record.define(input.personnelId(), input.trainingId(), input.completedOn(), renewal(input, training),
                input.score(), input.status());
        try {
            return records.saveAndFlush(record);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw Admin.versionMismatch();
        }
    }

    @Transactional
    public void deleteRecord(long id) {
        records.delete(records.findById(id).orElseThrow(Admin.notFound("TRAINING_RECORD_NOT_FOUND", "record", id)));
    }

    /** A person was deleted: their records go too. */
    @ApplicationModuleListener
    void on(PersonnelRemoved removed) {
        records.deleteByPersonnel(removed.personnelId());
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private Training find(long id) {
        return trainings.findById(id).orElseThrow(Admin.notFound("TRAINING_NOT_FOUND", "training", id));
    }

    private static LocalDate renewal(RecordInput input, Training training) {
        if (input.nextRenewalDate() != null || input.completedOn() == null || training.getValidityMonths() == null) {
            return input.nextRenewalDate();
        }
        return input.completedOn().plusMonths(training.getValidityMonths());
    }

    private static void apply(Training training, TrainingInput input) {
        training.define(input.name().strip(), Admin.text(input.category()), input.validityMonths(), input.mandatory(),
                Admin.text(input.internalNotes()));
    }

    private static void validate(TrainingInput input) {
        List<FieldProblem> problems = new ArrayList<>();
        Admin.required(problems, "name", input.name());
        Admin.maxLength(problems, "name", input.name(), 255);
        Admin.maxLength(problems, "category", input.category(), 255);
        Admin.maxLength(problems, "internalNotes", input.internalNotes(), 255);
        if (input.validityMonths() != null && (input.validityMonths() < 1 || input.validityMonths() > 120)) {
            problems.add(new FieldProblem("validityMonths", "OUT_OF_RANGE", "Validity is 1-120 months (empty: never expires)."));
        }
        Admin.check(problems);
    }

    private Training validate(RecordInput input) {
        List<FieldProblem> problems = new ArrayList<>();
        Training training = null;
        if (input.personnelId() == null) {
            problems.add(new FieldProblem("personnelId", "REQUIRED", "Choose a person."));
        } else if (!personnel.exists(input.personnelId())) {
            problems.add(new FieldProblem("personnelId", "PERSONNEL_NOT_FOUND", "The person does not exist."));
        }
        if (input.trainingId() == null) {
            problems.add(new FieldProblem("trainingId", "REQUIRED", "Choose a training."));
        } else {
            training = trainings.findById(input.trainingId()).orElse(null);
            if (training == null) {
                problems.add(new FieldProblem("trainingId", "TRAINING_NOT_FOUND", "The training does not exist."));
            }
        }
        if (input.status() == null || !RECORD_STATUSES.contains(input.status())) {
            problems.add(new FieldProblem("status", "INVALID_VALUE", "status is one of " + RECORD_STATUSES + "."));
        }
        if (input.score() != null && (input.score() < 0 || input.score() > 100)) {
            problems.add(new FieldProblem("score", "OUT_OF_RANGE", "The score is 0-100."));
        }
        if (input.completedOn() != null && input.completedOn().isAfter(LocalDate.now(clock))) {
            problems.add(new FieldProblem("completedOn", "OUT_OF_RANGE", "Completion cannot be in the future."));
        }
        if (input.completedOn() != null && input.nextRenewalDate() != null
                && input.nextRenewalDate().isBefore(input.completedOn())) {
            problems.add(new FieldProblem("nextRenewalDate", "OUT_OF_RANGE", "Renewal is before completion."));
        }
        Admin.check(problems);
        return training;
    }
}
