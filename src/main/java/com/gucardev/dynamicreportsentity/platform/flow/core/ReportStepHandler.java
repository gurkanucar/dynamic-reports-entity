package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.reporting.ReportViewer;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ReportExecutor;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ReportQuery;
import com.gucardev.dynamicreportsentity.platform.reporting.query.ResultColumn;
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
 * Runs the report as the flow owner, streams it to a temporary CSV file (memory stays flat) and stores the file.
 * Keeps the first {@code flows.preview-rows} rows in the output for the e-mail body.
 */
@Component
class ReportStepHandler implements StepHandler {

    private static final int MAX_CELL_LENGTH = 500;

    private final ReportExecutor executor;
    private final RunAsResolver runAs;
    private final ArtifactStore artifacts;
    private final FlowProperties properties;
    private final Clock clock;

    ReportStepHandler(ReportExecutor executor, RunAsResolver runAs, ArtifactStore artifacts,
                      FlowProperties properties, Clock clock) {
        this.executor = executor;
        this.runAs = runAs;
        this.artifacts = artifacts;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String type() {
        return ReportStep.TYPE;
    }

    @Override
    public boolean interruptible() {
        return false;   // stopped through the query handle; interrupting would break the JDBC connection
    }

    @Override
    public Map<String, Object> execute(StepContext context) throws IOException {
        ClaimedStep claimed = context.step();
        ReportStep step = (ReportStep) claimed.step();
        ReportViewer viewer = runAs.resolve(claimed.runAs());
        ReportQuery query = executor.prepare(step.definition(), viewer);
        int maxRows = step.maxRows() == null ? properties.reportMaxRows()
                : Math.min(step.maxRows(), properties.reportMaxRows());
        Duration timeout = Duration.between(clock.instant(), context.deadline());
        if (timeout.toSeconds() < 1) {
            timeout = Duration.ofSeconds(1);
        }

        artifacts.deleteForStep(claimed.stepRunId());    // left by an earlier attempt
        Path file = Files.createTempFile("flow-run-" + claimed.runId() + "-", ".csv");
        try {
            List<Map<String, Object>> preview = new ArrayList<>();
            long rows;
            try (OutputStream out = new SizeLimitedOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(file)), properties.maxArtifactSize().toBytes())) {
                rows = executor.exportCsv(query, out, maxRows, timeout, context.queryHandle(), row -> {
                    if (preview.size() < properties.previewRows()) {
                        preview.add(Map.of("cells", row.stream().map(ReportStepHandler::display).toList()));
                    }
                });
            }
            String fileName = step.fileNameOrDefault();
            RunArtifact artifact = artifacts.save(claimed.runId(), claimed.stepRunId(), fileName, "text/csv", file);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("rowCount", rows);
            output.put("hasRows", rows > 0);
            output.put("fileName", fileName);
            output.put("artifactId", artifact.getId());
            output.put("sizeBytes", artifact.getSizeBytes());
            output.put("expiresAt", artifact.getExpiresAt().toString());
            output.put("columns", query.columns().stream().map(ResultColumn::label).toList());
            output.put("rows", preview);
            output.put("previewTruncated", rows > preview.size());
            output.put("generatedAt", Instant.now(clock).toString());
            return output;
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** Cell text for e-mails: LIST cells joined, long values shortened. */
    private static String display(Object value) {
        String text = switch (value) {
            case null -> "";
            case Collection<?> list -> String.join("; ", list.stream().map(String::valueOf).toList());
            default -> value.toString();
        };
        return Texts.truncate(text, MAX_CELL_LENGTH);
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
                throw new StepException("REPORT_FILE_TOO_LARGE", "The report file is larger than "
                        + (limit / (1024 * 1024)) + " MB; narrow the filters or choose fewer columns", false);
            }
        }
    }
}
