package com.gucardev.dynamicreportsentity.platform.dataset.query;

import com.gucardev.dynamicreportsentity.platform.dataset.catalog.FieldType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Excel workbook, streamed: only the last 100 rows are kept in memory (SXSSF), the rest goes to a temp file that is
 * deleted at the end. Numbers, dates and booleans are real cells (sortable, summable); text is always text, never a
 * formula. Bold header, frozen first row, filter buttons and column widths from the content.
 */
final class XlsxWriter implements TableWriter {

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

    XlsxWriter(OutputStream out) {
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
                boolean whole = number.doubleValue() == Math.rint(number.doubleValue());
                if (!whole) {
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
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(text);
    }

    @Override
    public void finish() {
        try {
            if (!columns.isEmpty() && rowIndex > 1) {
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rowIndex - 1, 0,
                        columns.size() - 1));
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
