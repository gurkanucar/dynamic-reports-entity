package com.gucardev.dynamicreportsentity.reporting.query;

/** The report definition cannot be turned into a query (unknown field, wrong operator, bad value...). */
public class InvalidReportException extends RuntimeException {

    public InvalidReportException(String message) {
        super(message);
    }
}
