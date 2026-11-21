package com.gucardev.dynamicreportsentity.platform.dataset.query;

/** The dataset definition cannot be turned into a query (unknown field, wrong operator, bad value...). */
public class InvalidDatasetException extends RuntimeException {

    public InvalidDatasetException(String message) {
        super(message);
    }
}
