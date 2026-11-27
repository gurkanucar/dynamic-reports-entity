package com.gucardev.dynamicreportsentity.platform.flowdataset;

import com.gucardev.entityreports.Dataset;
import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowStep;
import com.gucardev.dynamicreportsentity.platform.flow.spi.Lane;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Runs a dataset as the flow's owner (their field and row permissions apply) and stores the result as a CSV
 * file. Output for later steps: {@code rowCount}, {@code hasRows}, {@code fileName}, {@code columns}, the first
 * rows as {@code rows[].cells}, {@code previewTruncated} and the file itself (for mail attachments).
 *
 * The dataset is either a saved one, used by name ({@code datasetName}: changing it changes every flow using it),
 * or designed for this step alone ({@code definition}); exactly one of the two.
 *
 * @param datasetName a saved dataset ({@code /api/saved-datasets}); always computed live, as the flow's owner
 * @param format      file format: {@code xlsx} (Excel) or {@code csv}; null = csv (flows saved before Excel)
 * @param fileName    name of the file, with the format's extension; default {@code <key>.xlsx} / {@code <key>.csv}
 * @param mailRows    how many first rows later steps get as {@code rows} (the table in a mail body), 0-500;
 *                    null = {@code flows.preview-rows}
 * @param maxRows  fail above this many rows; default and upper bound {@code flows.dataset-max-rows}
 */
public record DatasetStep(
        @NotBlank @Pattern(regexp = KEY_PATTERN, message = KEY_MESSAGE) String key,
        @Pattern(regexp = "[a-z][a-z0-9_]{1,39}", message = "must be the name of a saved dataset") String datasetName,
        @Valid Dataset.Definition definition,
        Dataset.ExportFormat format,
        @Size(max = 100) @Pattern(regexp = "[\\w .-]+\\.(csv|xlsx)", message = "must end with .xlsx or .csv and "
                + "contain only letters, digits, spaces, '.', '_' or '-'") String fileName,
        @Positive Integer maxRows,
        @Min(0) @Max(500) Integer mailRows,
        @Min(1) @Max(3600) Integer timeoutSeconds,
        @Min(1) @Max(10) Integer maxAttempts) implements FlowStep {

    public static final String TYPE = "dataset";

    @Override
    @JsonIgnore
    public String typeName() {
        return TYPE;
    }

    @Override
    @JsonIgnore
    public Lane lane() {
        return Lane.HEAVY;
    }

    @JsonIgnore
    public Dataset.ExportFormat formatOrDefault() {
        return format == null ? Dataset.ExportFormat.CSV : format;
    }

    @JsonIgnore
    public String fileNameOrDefault() {
        return fileName == null ? key + formatOrDefault().extension() : fileName;
    }
}
