package com.gucardev.dynamicreportsentity.platform.flowdataset;

import com.gucardev.entityreports.Dataset;
import com.gucardev.entityreports.DatasetExecutor;
import com.gucardev.entityreports.SavedDatasets;
import com.gucardev.dynamicreportsentity.platform.api.ApiException;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepCheck;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepReference;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Dataset steps are built (not run) when a flow is saved, for the editor and for the owner the flow runs as:
 * a field either of them may not see is refused with 403 {@code DATASET_FIELD_FORBIDDEN}.
 */
@Component
@EnableConfigurationProperties(FlowDatasetProperties.class)
class DatasetStepType implements StepType<DatasetStep> {

    static final String SAVED_DATASET = "saved-dataset";

    private final DatasetExecutor datasets;
    private final SavedDatasets saved;
    private final FlowDatasetProperties properties;

    DatasetStepType(DatasetExecutor datasets, SavedDatasets saved, FlowDatasetProperties properties) {
        this.datasets = datasets;
        this.saved = saved;
        this.properties = properties;
    }

    @Override
    public String name() {
        return DatasetStep.TYPE;
    }

    @Override
    public Class<DatasetStep> stepClass() {
        return DatasetStep.class;
    }

    @Override
    public boolean producesFiles() {
        return true;
    }

    @Override
    public void validate(DatasetStep step, StepCheck check) {
        if (step.maxRows() != null && step.maxRows() > properties.datasetMaxRows()) {
            check.problem("maxRows", "MAX_ROWS_TOO_HIGH",
                    "At most " + properties.datasetMaxRows() + " rows are allowed.");
        }
        if (step.fileName() != null && !step.fileName().endsWith(step.formatOrDefault().extension())) {
            check.problem("fileName", "FILE_EXTENSION_MISMATCH",
                    "The file name must end with " + step.formatOrDefault().extension() + " for this format.");
        }
        if ((step.datasetName() == null) == (step.definition() == null)) {
            check.problem("", "DATASET_SOURCE_REQUIRED",
                    "Choose a saved dataset (datasetName) or design one for this step (definition), not both.");
            return;
        }
        Dataset.Definition definition = step.definition();
        String field = "definition";
        if (step.datasetName() != null) {
            field = "datasetName";
            definition = saved.find(step.datasetName()).map(SavedDatasets.Info::definition).orElse(null);
            if (definition == null) {
                check.problem(field, "SAVED_DATASET_NOT_FOUND", "There is no saved dataset '" + step.datasetName() + "'.");
                return;
            }
        }
        try {
            datasets.prepare(definition, check.editor());
            if (check.owner() != null && check.owner() != check.editor()) {
                datasets.prepare(definition, check.owner());
            }
        } catch (Dataset.InvalidException e) {
            check.problem(field, "INVALID_DATASET_DEFINITION", e.getMessage());
        } catch (Dataset.AccessDeniedException e) {
            throw new ApiException(ApiException.Kind.FORBIDDEN, "DATASET_FIELD_FORBIDDEN",
                    "Step '" + step.key() + "': " + e.getMessage());
        }
    }

    @Override
    public Set<StepReference> references(DatasetStep step) {
        return step.datasetName() == null ? Set.of() : Set.of(new StepReference(SAVED_DATASET, step.datasetName()));
    }
}
