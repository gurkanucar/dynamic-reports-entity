package com.gucardev.dynamicreportsentity.platform.flowdataset;

import com.gucardev.entityreports.Dataset;
import com.gucardev.entityreports.DatasetExecutor;
import com.gucardev.entityreports.SavedDatasets;
import com.gucardev.dynamicreportsentity.platform.flow.spi.ClaimedStep;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepResult;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepException;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepHandler;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepContext;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import com.gucardev.dynamicreportsentity.platform.identity.UserDirectory;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the dataset as the flow owner, streams it to a temporary CSV file (memory stays flat) and stores the file.
 * Keeps the first {@code flows.preview-rows} rows in the output for the e-mail body.
 */
@Component
class DatasetStepHandler implements StepHandler {

    private static final int MAX_CELL_LENGTH = 500;

    private final DatasetExecutor executor;
    private final SavedDatasets saved;
    private final UserDirectory users;
    private final FlowDatasetProperties properties;
    private final Clock clock;

    DatasetStepHandler(DatasetExecutor executor, SavedDatasets saved, UserDirectory users,
                       FlowDatasetProperties properties, Clock clock) {
        this.executor = executor;
        this.saved = saved;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String type() {
        return DatasetStep.TYPE;
    }

    @Override
    public boolean interruptible() {
        return false;   // stopped through the query handle; interrupting would break the JDBC connection
    }

    @Override
    public StepResult execute(StepContext context) throws IOException {
        ClaimedStep claimed = context.step();
        DatasetStep step = (DatasetStep) claimed.step();
        Actor viewer = users.find(claimed.runAs()).orElseThrow(() -> new StepException("RUN_AS_USER_UNAVAILABLE",
                "The flow owner '" + claimed.runAs() + "' no longer exists or is disabled", false));
        int maxRows = step.maxRows() == null ? properties.datasetMaxRows()
                : Math.min(step.maxRows(), properties.datasetMaxRows());
        int mailRows = step.mailRows() == null ? properties.previewRows() : step.mailRows();
        Duration timeout = Duration.between(clock.instant(), context.deadline());
        if (timeout.toSeconds() < 1) {
            timeout = Duration.ofSeconds(1);
        }
        DatasetExecutor.QueryHandle handle = new DatasetExecutor.QueryHandle();
        context.onStop(handle::cancel);

        Path file = Files.createTempFile("flow-run-" + claimed.runId() + "-", step.formatOrDefault().extension());
        try {
            Dataset.Query query = executor.prepare(definition(step), viewer);
            List<Map<String, Object>> preview = new ArrayList<>();
            long rows;
            try (OutputStream out = new SizeLimitedOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(file)), properties.maxArtifactSize().toBytes())) {
                rows = executor.export(query, step.formatOrDefault(), out, maxRows, timeout, handle, row -> {
                    if (preview.size() < mailRows) {
                        preview.add(Map.of("cells", row.stream().map(DatasetStepHandler::display).toList()));
                    }
                });
            }
            String fileName = step.fileNameOrDefault();
            StepContext.StoredFile stored = context.storeFile(fileName, step.formatOrDefault().contentType(), file);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("rowCount", rows);
            output.put("hasRows", rows > 0);
            output.put("fileName", fileName);
            output.put("contentType", step.formatOrDefault().contentType());
            output.put("artifactId", stored.id());
            output.put("sizeBytes", stored.sizeBytes());
            output.put("expiresAt", stored.expiresAt().toString());
            output.put("columns", query.columns().stream().map(Dataset.ResultColumn::label).toList());
            output.put("rows", preview);
            output.put("previewTruncated", rows > preview.size());
            output.put("generatedAt", Instant.now(clock).toString());
            return StepResult.done(output);
        } catch (Dataset.InvalidException e) {
            throw new StepException("INVALID_DATASET_DEFINITION", e.getMessage(), false);
        } catch (Dataset.AccessDeniedException e) {
            throw new StepException("DATASET_ACCESS_DENIED", e.getMessage(), false);
        } catch (Dataset.CancelledException e) {
            throw new StepException("STEP_INTERRUPTED", "The query was stopped", true);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** The step's own definition, or the saved dataset's current one (a saved dataset always runs live here). */
    private Dataset.Definition definition(DatasetStep step) {
        if (step.datasetName() == null) {
            return step.definition();
        }
        return saved.find(step.datasetName()).map(SavedDatasets.Info::definition)
                .orElseThrow(() -> new StepException("SAVED_DATASET_NOT_FOUND", "Saved dataset '"
                        + step.datasetName() + "' no longer exists", false));
    }

    /** Cell text for e-mails: LIST cells joined, long values shortened. */
    private static String display(Object value) {
        String text = switch (value) {
            case null -> "";
            case Collection<?> list -> String.join("; ", list.stream().map(String::valueOf).toList());
            // averages come as long fractions (83.33333333333333): two decimals, no trailing zeros (94.0 -> 94)
            case Double number when Double.isFinite(number) -> new java.math.BigDecimal(number).setScale(2, java.math.RoundingMode.HALF_UP)
                    .stripTrailingZeros().toPlainString();
            case Float number -> display(number.doubleValue());
            case java.math.BigDecimal number when number.scale() > 2 ->
                    number.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            default -> value.toString();
        };
        return text.length() <= MAX_CELL_LENGTH ? text : text.substring(0, MAX_CELL_LENGTH - 1) + "…";
    }

    /** Fails the step (no retry: the same data would be just as big) once the file exceeds the limit. */
    private static final class SizeLimitedOutputStream extends FilterOutputStream {

        private final long limit;
        private long written;

        SizeLimitedOutputStream(OutputStream out, long limit) {
            super(out);
            this.limit = limit;
        }

        @Override
        public void write(int b) throws IOException {
            count(1);
            out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            count(len);
            out.write(b, off, len);
        }

        private void count(int bytes) {
            written += bytes;
            if (written > limit) {
                throw new StepException("DATASET_FILE_TOO_LARGE", "The dataset file is larger than "
                        + (limit / (1024 * 1024)) + " MB; narrow the filters or choose fewer columns", false);
            }
        }
    }
}
