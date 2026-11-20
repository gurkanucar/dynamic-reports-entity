package com.gucardev.dynamicreportsentity.reporting.query;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/** Minimal RFC 4180 CSV writer that also neutralises spreadsheet formula injection. */
final class CsvWriter {

    private final Writer out;

    CsvWriter(Writer out) {
        this.out = out;
    }

    void writeRow(List<?> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.write(',');
            }
            out.write(cell(values.get(i)));
        }
        out.write("\r\n");
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        // Text starting with = + - @ would be executed as a formula by Excel.
        if (value instanceof CharSequence && !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            text = '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
