package com.gucardev.dynamicreportsentity.platform.flowdataset;

import com.gucardev.entityreports.SavedDatasets;
import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowReferences;
import org.springframework.stereotype.Component;

/** Tells the dataset module whether a flow step uses a saved dataset (it then cannot be deleted). */
@Component
class FlowSavedDatasetUsage implements SavedDatasets.Usage {

    private final FlowReferences references;

    FlowSavedDatasetUsage(FlowReferences references) {
        this.references = references;
    }

    @Override
    public boolean usesSavedDataset(String name) {
        return references.isReferenced(DatasetStepType.SAVED_DATASET, name);
    }
}
