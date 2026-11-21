package com.gucardev.dynamicreportsentity.platform.dataset.query;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/** Writes a dataset as a table, row by row, in one of the {@link ExportFormat}s. */
interface TableWriter {

    void header(List<ResultColumn> columns);

    void row(List<Object> values);

    /** A last line saying something about the data (e.g. that it was cut off). */
    void note(String text);

    /** Writes what is buffered; the stream stays open. */
    void finish();

    static TableWriter of(ExportFormat format, OutputStream out) {
        return format == ExportFormat.XLSX ? new XlsxWriter(out) : new Csv(out);
    }

    /** CSV with a BOM, so Excel detects UTF-8. */
    final class Csv implements TableWriter {

        private final Writer writer;
        private final CsvWriter csv;

        Csv(OutputStream out) {
            this.writer = new BufferedWriter(new OutputStreamWriter(out, UTF_8));
            this.csv = new CsvWriter(writer);
        }

        @Override
        public void header(List<ResultColumn> columns) {
            try {
                writer.write('﻿');
                csv.writeRow(columns.stream().map(ResultColumn::label).toList());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void row(List<Object> values) {
            try {
                csv.writeRow(values);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void note(String text) {
            row(List.of(text));
        }

        @Override
        public void finish() {
            try {
                writer.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
