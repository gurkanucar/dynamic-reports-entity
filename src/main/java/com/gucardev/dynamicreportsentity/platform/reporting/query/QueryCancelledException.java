package com.gucardev.dynamicreportsentity.platform.reporting.query;

/** A background query was stopped through its {@link QueryHandle}. */
public class QueryCancelledException extends RuntimeException {

    public QueryCancelledException() {
        super("The query was cancelled");
    }
}
