package com.gucardev.dynamicreportsentity.platform.flowdataset;

import com.gucardev.dynamicreportsentity.platform.dataset.snapshot.SnapshotUsage;
import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowReferences;
import org.springframework.stereotype.Component;

/** Tells the dataset module whether a flow step uses a saved dataset (it then cannot be deleted). */
@Component
class FlowSavedDatasetUsage implements SnapshotUsage {

    private final FlowReferences references;

    FlowSavedDatasetUsage(FlowReferences references) {
        this.references = references;
    }

    @Override
    public boolean usesSnapshot(String name) {
        return references.isReferenced(DatasetStepType.SAVED_DATASET, name);
    }
}
