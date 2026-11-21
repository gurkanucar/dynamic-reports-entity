package com.gucardev.dynamicreportsentity.platform.dataset.saved;

import com.gucardev.dynamicreportsentity.platform.api.ApiException;
import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetDefinition;
import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetExecutor;
import com.gucardev.dynamicreportsentity.platform.dataset.query.InvalidDatasetException;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Saved datasets. Anyone with PLATFORM_USER may save one (the definition must be valid for them); only the owner or an
 * admin may change or delete it; one in use ({@link SavedDatasetUsage}) cannot be deleted. Nothing is stored but the
 * definition: whoever runs it (a user on screen, a flow as its owner) gets the rows their permissions allow.
 */
@Service
public class SavedDatasetService {

    static final String ADMIN = "PLATFORM_ADMIN";
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{1,39}");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SavedDatasetRepository repository;
    private final DatasetExecutor executor;
    private final Clock clock;
    private List<SavedDatasetUsage> usages = List.of();

    SavedDatasetService(SavedDatasetRepository repository, DatasetExecutor executor, Clock clock) {
        this.repository = repository;
        this.executor = executor;
        this.clock = clock;
    }

    @Autowired(required = false)
    void setUsages(List<SavedDatasetUsage> usages) {
        this.usages = List.copyOf(usages);
    }

    public List<SavedDatasetInfo> list() {
        return repository.findAllByOrderByNameAsc().stream().map(SavedDatasetService::info).toList();
    }

    public SavedDatasetInfo get(String name) {
        return info(load(name));
    }

    public Optional<SavedDatasetInfo> find(String name) {
        return repository.findById(name).map(SavedDatasetService::info);
    }

    @Transactional
    public SavedDatasetInfo create(SavedDatasetRequest request, Actor editor) {
        String name = request.name();
        if (name == null || !NAME.matcher(name).matches()) {
            throw new InvalidDatasetException("The name must be 2-40 lower case letters, digits or underscores, "
                    + "starting with a letter (e.g. department_headcount)");
        }
        executor.prepare(request.definition(), editor);
        if (repository.existsById(name)) {
            throw nameTaken(name);
        }
        SavedDataset dataset = new SavedDataset(name, editor.username(), clock.instant());
        dataset.define(request.label(), request.description(), JSON.writeValueAsString(request.definition()),
                editor.username(), clock.instant());
        try {
            return info(repository.saveAndFlush(dataset));
        } catch (DataIntegrityViolationException e) {
            throw nameTaken(name);   // created at the same time elsewhere
        }
    }

    @Transactional
    public SavedDatasetInfo update(String name, SavedDatasetRequest request, Actor editor) {
        SavedDataset dataset = load(name);
        requireOwner(dataset, editor);
        executor.prepare(request.definition(), editor);
        dataset.define(request.label(), request.description(), JSON.writeValueAsString(request.definition()),
                editor.username(), clock.instant());
        try {
            return info(repository.saveAndFlush(dataset));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new SavedDatasetException(ApiException.Kind.CONFLICT, "VERSION_MISMATCH",
                    "Dataset '" + name + "' was changed by someone else; reload it and try again");
        }
    }

    /** Not while something uses it (e.g. a flow step). */
    @Transactional
    public void delete(String name, Actor editor) {
        SavedDataset dataset = load(name);
        requireOwner(dataset, editor);
        if (usages.stream().anyMatch(usage -> usage.usesSavedDataset(name))) {
            throw new SavedDatasetException(ApiException.Kind.CONFLICT, "SAVED_DATASET_IN_USE",
                    "Dataset '" + name + "' is used by a flow; change those flows first");
        }
        repository.delete(dataset);
    }

    private static void requireOwner(SavedDataset dataset, Actor editor) {
        if (!editor.hasAuthority(ADMIN) && !editor.username().equals(dataset.getCreatedBy())) {
            throw new SavedDatasetException(ApiException.Kind.FORBIDDEN, "NOT_DATASET_OWNER",
                    "Only " + dataset.getCreatedBy() + " or an admin may change dataset '" + dataset.getName() + "'");
        }
    }

    private SavedDataset load(String name) {
        return repository.findById(name).orElseThrow(() -> new SavedDatasetException(
                ApiException.Kind.NOT_FOUND, "SAVED_DATASET_NOT_FOUND", "There is no dataset '" + name + "'"));
    }

    private static SavedDatasetException nameTaken(String name) {
        return new SavedDatasetException(ApiException.Kind.CONFLICT, "SAVED_DATASET_NAME_TAKEN",
                "Dataset '" + name + "' already exists");
    }

    private static SavedDatasetInfo info(SavedDataset dataset) {
        return new SavedDatasetInfo(dataset.getName(), dataset.getLabel(), dataset.getDescription(),
                JSON.readValue(dataset.getDefinitionJson(), DatasetDefinition.class), dataset.getVersion(),
                dataset.getCreatedBy(), dataset.getCreatedAt(), dataset.getUpdatedBy(), dataset.getUpdatedAt());
    }
}
