package com.gucardev.dynamicreportsentity.platform.dataset.web;

import com.gucardev.dynamicreportsentity.platform.dataset.DatasetAccessDeniedException;
import com.gucardev.dynamicreportsentity.platform.dataset.snapshot.SnapshotException;
import com.gucardev.dynamicreportsentity.platform.dataset.query.InvalidDatasetException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {DatasetController.class, SnapshotController.class})
class DatasetExceptionHandler {

    @ExceptionHandler(InvalidDatasetException.class)
    ProblemDetail invalidDataset(InvalidDatasetException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid dataset definition", exception.getMessage());
    }

    @ExceptionHandler(DatasetAccessDeniedException.class)
    ProblemDetail accessDenied(DatasetAccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", exception.getMessage());
    }

    @ExceptionHandler(SnapshotException.class)
    ProblemDetail dataset(SnapshotException exception) {
        return switch (exception.kind()) {
            case NOT_FOUND -> problem(HttpStatus.NOT_FOUND, "Dataset not found", exception.getMessage());
            case CONFLICT -> problem(HttpStatus.CONFLICT, "Dataset conflict", exception.getMessage());
            case FORBIDDEN -> problem(HttpStatus.FORBIDDEN, "Not allowed", exception.getMessage());
        };
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
