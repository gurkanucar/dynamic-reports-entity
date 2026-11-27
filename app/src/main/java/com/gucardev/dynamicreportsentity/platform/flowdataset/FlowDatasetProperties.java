package com.gucardev.dynamicreportsentity.platform.flowdataset;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Settings of dataset steps (under {@code flows.*}, next to the engine's).
 *
 * @param datasetMaxRows   a dataset step fails above this many rows (a step may ask for less)
 * @param maxArtifactSize largest file a dataset step may produce (attached to mails, so keep it modest)
 * @param previewRows     rows of a dataset kept for the e-mail body ({@code steps.<key>.rows})
 */
@ConfigurationProperties("flows")
public record FlowDatasetProperties(@DefaultValue("100000") int datasetMaxRows,
                                   @DefaultValue("20MB") DataSize maxArtifactSize,
                                   @DefaultValue("20") int previewRows) {
}
