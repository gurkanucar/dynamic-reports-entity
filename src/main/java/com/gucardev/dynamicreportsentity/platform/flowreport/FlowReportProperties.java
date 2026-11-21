package com.gucardev.dynamicreportsentity.platform.flowreport;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Settings of report steps (under {@code flows.*}, next to the engine's).
 *
 * @param reportMaxRows   a report step fails above this many rows (a step may ask for less)
 * @param maxArtifactSize largest file a report step may produce (attached to mails, so keep it modest)
 * @param previewRows     rows of a report kept for the e-mail body ({@code steps.<key>.rows})
 */
@ConfigurationProperties("flows")
public record FlowReportProperties(@DefaultValue("100000") int reportMaxRows,
                                   @DefaultValue("20MB") DataSize maxArtifactSize,
                                   @DefaultValue("20") int previewRows) {
}
