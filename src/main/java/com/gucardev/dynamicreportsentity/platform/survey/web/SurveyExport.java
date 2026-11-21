package com.gucardev.dynamicreportsentity.platform.survey.web;

import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import com.gucardev.dynamicreportsentity.platform.survey.Survey;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyDefinition.Field;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Writes a survey's responses as an Excel sheet, streaming (a few rows in memory at a time). */
final class SurveyExport {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("Europe/Istanbul"));

    private SurveyExport() {
    }

    static void write(Survey survey, Actor viewer, SurveyService surveys, OutputStream out) throws IOException {
        List<Field> fields = survey.definition().answerableFields();
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Yanıtlar");
            CellStyle bold = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            bold.setFont(font);
            Row header = sheet.createRow(0);
            int column = 0;
            header.createCell(column++).setCellValue("Yanıt no");
            header.createCell(column++).setCellValue("Gönderilme");
            if (!survey.isAnonymous()) {
                header.createCell(column++).setCellValue("Gönderen");
            }
            for (Field field : fields) {
                header.createCell(column++).setCellValue(field.label());
            }
            header.forEach(cell -> cell.setCellStyle(bold));
            sheet.createFreezePane(0, 1);
            int[] rowIndex = {1};
            surveys.forEachResponse(survey.getId(), viewer, row -> {
                Row line = sheet.createRow(rowIndex[0]++);
                int at = 0;
                line.createCell(at++).setCellValue(row.response().getId().toString());
                line.createCell(at++).setCellValue(TIME.format(row.response().getSubmittedAt()));
                if (!survey.isAnonymous()) {
                    line.createCell(at++).setCellValue(row.response().getRespondent());
                }
                for (Field field : fields) {
                    Cell cell = line.createCell(at++);
                    Object value = row.answers().get(field.id());
                    if (value instanceof Number number && !field.choice()) {
                        cell.setCellValue(number.doubleValue());
                    } else if (value != null) {
                        cell.setCellValue(row.display().get(field.id()));
                    }
                }
            });
            for (int i = 0; i < Math.min(column, 30); i++) {
                sheet.setColumnWidth(i, i < 2 ? 18 * 256 : 28 * 256);
            }
            workbook.write(out);
        }
    }
}
