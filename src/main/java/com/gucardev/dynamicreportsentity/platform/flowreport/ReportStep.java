package com.gucardev.dynamicreportsentity.platform.flowreport;

import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowStep;
import com.gucardev.dynamicreportsentity.platform.flow.spi.Lane;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ReportDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Runs a report as the flow's owner (their field and row permissions apply) and stores the result as a CSV
 * file. Output for later steps: {@code rowCount}, {@code hasRows}, {@code fileName}, {@code columns}, the first
 * rows as {@code rows[].cells}, {@code previewTruncated} and the file itself (for mail attachments).
 *
 * @param fileName name of the CSV file; default {@code <key>.csv}
 * @param maxRows  fail above this many rows; default and upper bound {@code flows.report-max-rows}
 */
public record ReportStep(
        @NotBlank @Pattern(regexp = KEY_PATTERN, message = KEY_MESSAGE) String key,
        @NotNull @Valid ReportDefinition definition,
        @Size(max = 100) @Pattern(regexp = "[\\w .-]+\\.csv", message = "must end with .csv and contain only "
                + "letters, digits, spaces, '.', '_' or '-'") String fileName,
        @Positive Integer maxRows,
        @Min(1) @Max(3600) Integer timeoutSeconds,
        @Min(1) @Max(10) Integer maxAttempts) implements FlowStep {

    public static final String TYPE = "report";

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
