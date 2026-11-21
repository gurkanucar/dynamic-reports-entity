package com.gucardev.dynamicreportsentity.platform.dataset.query;

/** A background query was stopped through its {@link QueryHandle}. */
public class QueryCancelledException extends RuntimeException {

    public QueryCancelledException() {
        super("The query was cancelled");
    }
}
