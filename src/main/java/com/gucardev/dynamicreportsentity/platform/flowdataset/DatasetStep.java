package com.gucardev.dynamicreportsentity.platform.flowdataset;

import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowStep;
import com.gucardev.dynamicreportsentity.platform.flow.spi.Lane;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gucardev.dynamicreportsentity.platform.dataset.query.DatasetDefinition;
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
 * @param datasetName a saved dataset ({@code /api/snapshots}); always computed live, as the flow's owner
 * @param fileName    name of the CSV file; default {@code <key>.csv}
 * @param maxRows  fail above this many rows; default and upper bound {@code flows.dataset-max-rows}
 */
public record DatasetStep(
        @NotBlank @Pattern(regexp = KEY_PATTERN, message = KEY_MESSAGE) String key,
        @Pattern(regexp = "[a-z][a-z0-9_]{1,39}", message = "must be the name of a saved dataset") String datasetName,
        @Valid DatasetDefinition definition,
        @Size(max = 100) @Pattern(regexp = "[\\w .-]+\\.csv", message = "must end with .csv and contain only "
                + "letters, digits, spaces, '.', '_' or '-'") String fileName,
        @Positive Integer maxRows,
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
    public String fileNameOrDefault() {
        return fileName == null ? key + ".csv" : fileName;
    }
}
