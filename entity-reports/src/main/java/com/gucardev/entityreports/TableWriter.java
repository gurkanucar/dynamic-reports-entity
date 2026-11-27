package com.gucardev.entityreports;

import com.gucardev.entityreports.Dataset.ExportFormat;
import com.gucardev.entityreports.Dataset.FieldType;
import com.gucardev.entityreports.Dataset.ResultColumn;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        return format == ExportFormat.XLSX ? new Xlsx(out) : new Csv(out);
    }

    /**
     * CSV (RFC 4180 quoting) that also neutralises spreadsheet formula injection. Fields are separated by a semicolon:
     * Excel with Turkish (and most European) settings expects it, and with a comma it puts every row into a single
     * column. Starts with a UTF-8 BOM so Excel reads Turkish letters right.
     */
    final class Csv implements TableWriter {

        static final char SEPARATOR = ';';

        private final Writer writer;

        Csv(OutputStream out) {
            this.writer = new BufferedWriter(new OutputStreamWriter(out, UTF_8));
        }

        @Override
        public void header(List<ResultColumn> columns) {
            write('﻿' + line(columns.stream().map(ResultColumn::label).toList()));
        }

        @Override
        public void row(List<Object> values) {
            write(line(values));
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

        private void write(String text) {
            try {
                writer.write(text);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static String line(List<?> values) {
            return values.stream().map(Csv::cell).collect(Collectors.joining(String.valueOf(SEPARATOR))) + "\r\n";
        }

        static String cell(Object value) {
            if (value == null) {
                return "";
            }
            if (value instanceof Collection<?> list) {
                // LIST columns: one cell, values separated by "; "
                value = String.join("; ", list.stream().map(String::valueOf).toList());
            }
            String text = value.toString();
            // Text starting with = + - @ would be executed as a formula by Excel.
            if (value instanceof CharSequence && !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
                text = "'" + text;
            }
            if (text.indexOf(SEPARATOR) >= 0 || text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                    || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
                text = '"' + text.replace("\"", "\"\"") + '"';
            }
            return text;
        }
    }

    /**
     * Excel workbook, streamed: only the last 100 rows are kept in memory (SXSSF), the rest goes to a temp file that
     * is deleted at the end. Numbers, dates and booleans are real cells (sortable, summable); text is always text,
     * never a formula. Bold header, frozen first row, filter buttons and column widths from the content.
     */
    final class Xlsx implements TableWriter {

        /** Excel's hard limit is 32767 characters per cell. */
        private static final int MAX_CELL = 32_000;
        private static final int MAX_WIDTH_CHARS = 60;

        private final OutputStream out;
        private final SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        private final SXSSFSheet sheet = workbook.createSheet("Veri");
        private final CellStyle headerStyle;
        private final CellStyle dateStyle;
        private final CellStyle dateTimeStyle;
        private final CellStyle decimalStyle;
        private List<ResultColumn> columns = List.of();
        private int[] widths = new int[0];
        private int rowIndex;

        Xlsx(OutputStream out) {
            this.out = out;
            workbook.setCompressTempFiles(true);
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle = workbook.createCellStyle();
            headerStyle.setFont(bold);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd.mm.yyyy"));
            dateTimeStyle = workbook.createCellStyle();
            dateTimeStyle.setDataFormat(workbook.createDataFormat().getFormat("dd.mm.yyyy hh:mm"));
            decimalStyle = workbook.createCellStyle();
            decimalStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.##"));
        }

        @Override
        public void header(List<ResultColumn> columns) {
            this.columns = columns;
            this.widths = new int[columns.size()];
            Row row = sheet.createRow(rowIndex++);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = row.createCell(i);
                cell.setCellValue(columns.get(i).label());
                cell.setCellStyle(headerStyle);
                widths[i] = columns.get(i).label().length() + 2;
            }
            sheet.createFreezePane(0, 1);
        }

        @Override
        public void row(List<Object> values) {
            Row row = sheet.createRow(rowIndex++);
            for (int i = 0; i < values.size(); i++) {
                Object value = values.get(i);
                if (value == null) {
                    continue;
                }
                Cell cell = row.createCell(i);
                int length = write(cell, value, i < columns.size() ? columns.get(i).type() : null);
                if (i < widths.length) {
                    widths[i] = Math.max(widths[i], Math.min(length + 1, MAX_WIDTH_CHARS));
                }
            }
        }

        /** @return about how many characters the value shows */
        private int write(Cell cell, Object value, FieldType type) {
            switch (value) {
                case Number number when type != FieldType.TEXT -> {
                    cell.setCellValue(number.doubleValue());
                    if (number.doubleValue() != Math.rint(number.doubleValue())) {
                        cell.setCellStyle(decimalStyle);
                    }
                    return number.toString().length();
                }
                case Boolean bool -> {
                    cell.setCellValue(bool ? "Evet" : "Hayır");
                    return 5;
                }
                case LocalDate date -> {
                    cell.setCellValue(date);
                    cell.setCellStyle(dateStyle);
                    return 10;
                }
                case LocalDateTime dateTime -> {
                    cell.setCellValue(dateTime);
                    cell.setCellStyle(dateTimeStyle);
                    return 16;
                }
                case OffsetDateTime dateTime -> {
                    return write(cell, dateTime.toLocalDateTime(), type);
                }
                case ZonedDateTime dateTime -> {
                    return write(cell, dateTime.toLocalDateTime(), type);
                }
                case java.sql.Date date -> {
                    return write(cell, date.toLocalDate(), type);
                }
                case java.sql.Timestamp timestamp -> {
                    return write(cell, timestamp.toLocalDateTime(), type);
                }
                default -> {
                    String text = text(value);
                    cell.setCellValue(text.length() > MAX_CELL ? text.substring(0, MAX_CELL) + "…" : text);
                    return text.length();
                }
            }
        }

        /** LIST columns: one cell, values (or "a · b" for objects) separated by "; ". */
        private static String text(Object value) {
            if (value instanceof Collection<?> list) {
                return list.stream().map(item -> item instanceof Map<?, ?> map
                        ? map.values().stream().map(String::valueOf).collect(Collectors.joining(" · "))
                        : String.valueOf(item)).collect(Collectors.joining("; "));
            }
            return value.toString();
        }

        @Override
        public void note(String text) {
            sheet.createRow(rowIndex++).createCell(0).setCellValue(text);
        }

        @Override
        public void finish() {
            try {
                if (!columns.isEmpty() && rowIndex > 1) {
                    sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, columns.size() - 1));
                }
                for (int i = 0; i < widths.length; i++) {
                    sheet.setColumnWidth(i, Math.min(widths[i], MAX_WIDTH_CHARS) * 256);
                }
                workbook.write(out);
                out.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                try {
                    workbook.close();
                } catch (IOException ignored) {
                    // only releases resources
                }
                workbook.dispose();
            }
        }
    }
}
