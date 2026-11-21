package com.gucardev.dynamicreportsentity.platform.dataset.web;

import com.gucardev.dynamicreportsentity.platform.dataset.DatasetAccessDeniedException;
import com.gucardev.dynamicreportsentity.platform.dataset.query.InvalidDatasetException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DatasetController.class)
class DatasetExceptionHandler {

    @ExceptionHandler(InvalidDatasetException.class)
    ProblemDetail invalidDataset(InvalidDatasetException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid dataset definition", exception.getMessage());
    }

    @ExceptionHandler(DatasetAccessDeniedException.class)
    ProblemDetail accessDenied(DatasetAccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", exception.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
