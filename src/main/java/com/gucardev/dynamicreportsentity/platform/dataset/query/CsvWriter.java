package com.gucardev.dynamicreportsentity.platform.dataset.query;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Minimal CSV writer (RFC 4180 quoting) that also neutralises spreadsheet formula injection. Fields are separated by
 * a semicolon: Excel with Turkish (and most European) settings expects it, and with a comma it puts every row into a
 * single column. Files start with a UTF-8 BOM (written by the caller) so Excel reads Turkish letters right.
 */
final class CsvWriter {

    static final char SEPARATOR = ';';

    private final Writer out;

    CsvWriter(Writer out) {
        this.out = out;
    }

    void writeRow(List<?> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.write(SEPARATOR);
            }
            out.write(cell(values.get(i)));
        }
        out.write("\r\n");
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof java.util.Collection<?> list) {
            // LIST columns: one cell, values separated by "; "
            value = String.join("; ", list.stream().map(String::valueOf).toList());
        }
        String text = value.toString();
        // Text starting with = + - @ would be executed as a formula by Excel.
        if (value instanceof CharSequence && !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.indexOf(SEPARATOR) >= 0 || text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            text = '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
