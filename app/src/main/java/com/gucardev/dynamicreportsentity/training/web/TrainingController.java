package com.gucardev.dynamicreportsentity.training.web;

import com.gucardev.dynamicreportsentity.platform.api.ApiExceptionHandlerSupport;
import com.gucardev.dynamicreportsentity.platform.api.ApiResponse;
import com.gucardev.dynamicreportsentity.training.Training;
import com.gucardev.dynamicreportsentity.training.TrainingRecord;
import com.gucardev.dynamicreportsentity.training.TrainingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.etag;
import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.expectedVersion;
import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.paging;

/** Trainings and training records (platform admins). */
@RestController
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
class TrainingController {

    record TrainingRequest(@NotBlank @Size(max = 255) String name, @Size(max = 255) String category,
                           Integer validityMonths, Boolean mandatory, @Size(max = 255) String internalNotes) {
        TrainingService.TrainingInput input() {
            return new TrainingService.TrainingInput(name, category, validityMonths, Boolean.TRUE.equals(mandatory),
                    internalNotes);
        }
    }

    record RecordRequest(@NotNull Long personnelId, @NotNull Long trainingId, LocalDate completedOn,
                         LocalDate nextRenewalDate, Integer score, @NotNull String status) {
        TrainingService.RecordInput input() {
            return new TrainingService.RecordInput(personnelId, trainingId, completedOn, nextRenewalDate, score, status);
        }
    }

    record Ref(long id, String name) {
    }

    record TrainingView(long id, long version, String name, String category, Integer validityMonths,
                        boolean mandatory, String internalNotes, long records, long overdue) {
    }

    record RecordView(long id, long version, Ref personnel, Ref training, LocalDate completedOn,
                      LocalDate nextRenewalDate, Integer score, String status) {
    }

    private final TrainingService trainings;

    TrainingController(TrainingService trainings) {
        this.trainings = trainings;
    }

    /** Filters: {@code q} (name, category), {@code category}, {@code mandatory}; sort: name, category, validityMonths. */
    @GetMapping("/api/trainings")
    ApiResponse<List<TrainingView>> list(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) Boolean mandatory,
                                         @RequestParam(required = false) String sort,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        int[] paging = paging(page, size);
        return ApiResponse.page(trainings.list(q, category, mandatory, sort, paging[0], paging[1]),
                listed -> view(listed.training(), listed.records(), listed.overdue()));
    }

    @GetMapping("/api/trainings/lookup")
    ApiResponse<List<Ref>> lookup() {
        return ApiResponse.of(trainings.all().stream().map(t -> new Ref(t.getId(), t.getName())).toList());
    }

    @GetMapping("/api/trainings/categories")
    ApiResponse<List<String>> categories() {
        return ApiResponse.of(trainings.categories());
    }

    @PostMapping("/api/trainings")
    ResponseEntity<ApiResponse<TrainingView>> create(@Valid @RequestBody TrainingRequest request) {
        Training training = trainings.create(request.input());
        return ResponseEntity.created(URI.create("/api/trainings/" + training.getId())).eTag(etag(training.getVersion()))
                .body(ApiResponse.of(view(training, 0, 0)));
    }

    @PutMapping("/api/trainings/{id}")
    ResponseEntity<ApiResponse<TrainingView>> update(@PathVariable long id,
                                                     @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                     @Valid @RequestBody TrainingRequest request) {
        Training training = trainings.update(id, expectedVersion(ifMatch), request.input());
        return ResponseEntity.ok().eTag(etag(training.getVersion())).body(ApiResponse.of(view(training, 0, 0)));
    }

    @DeleteMapping("/api/trainings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        trainings.delete(id);
    }

    /**
     * Filters: {@code personnelId}, {@code trainingId}, {@code status} (repeatable), {@code due} (overdue | soon),
     * {@code completedFrom}/{@code completedTo}; sort: completedOn, nextRenewalDate, score, status.
     */
    @GetMapping("/api/training-records")
    ApiResponse<List<RecordView>> records(@RequestParam(required = false) Long personnelId,
                                          @RequestParam(required = false) Long trainingId,
                                          @RequestParam(required = false) List<String> status,
                                          @RequestParam(required = false) String due,
                                          @RequestParam(required = false) LocalDate completedFrom,
                                          @RequestParam(required = false) LocalDate completedTo,
                                          @RequestParam(required = false) String sort,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        int[] paging = paging(page, size);
        TrainingService.RecordFilter filter = new TrainingService.RecordFilter(personnelId, trainingId,
                status == null ? List.of() : status, due, completedFrom, completedTo, sort);
        return ApiResponse.page(trainings.records(filter, paging[0], paging[1]),
                listed -> view(listed.record(), listed.personnelName(), listed.trainingName()));
    }

    @PostMapping("/api/training-records")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<RecordView> createRecord(@Valid @RequestBody RecordRequest request) {
        return ApiResponse.of(view(trainings.createRecord(request.input()), null, null));
    }

    @PutMapping("/api/training-records/{id}")
    ResponseEntity<ApiResponse<RecordView>> updateRecord(@PathVariable long id,
                                                         @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                         @Valid @RequestBody RecordRequest request) {
        TrainingRecord record = trainings.updateRecord(id, expectedVersion(ifMatch), request.input());
        return ResponseEntity.ok().eTag(etag(record.getVersion())).body(ApiResponse.of(view(record, null, null)));
    }

    @DeleteMapping("/api/training-records/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteRecord(@PathVariable long id) {
        trainings.deleteRecord(id);
    }

    private static TrainingView view(Training training, long records, long overdue) {
        return new TrainingView(training.getId(), training.getVersion(), training.getName(), training.getCategory(),
                training.getValidityMonths(), Boolean.TRUE.equals(training.getMandatory()), training.getInternalNotes(),
                records, overdue);
    }

    private static RecordView view(TrainingRecord record, String personnelName, String trainingName) {
        return new RecordView(record.getId(), record.getVersion(), new Ref(record.getPersonnelId(), personnelName),
                new Ref(record.getTrainingId(), trainingName), record.getCompletedOn(), record.getNextRenewalDate(),
                record.getScore(), record.getStatus());
    }
}

@RestControllerAdvice(basePackageClasses = TrainingController.class)
class TrainingExceptionHandler extends ApiExceptionHandlerSupport {
}
