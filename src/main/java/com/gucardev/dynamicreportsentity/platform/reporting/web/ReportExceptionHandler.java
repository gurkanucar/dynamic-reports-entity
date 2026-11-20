package com.gucardev.dynamicreportsentity.platform.reporting.web;

import com.gucardev.dynamicreportsentity.platform.reporting.ReportAccessDeniedException;
import com.gucardev.dynamicreportsentity.platform.reporting.dataset.DatasetException;
import com.gucardev.dynamicreportsentity.platform.reporting.query.InvalidReportException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {ReportController.class, DatasetController.class})
class ReportExceptionHandler {

    @ExceptionHandler(InvalidReportException.class)
    ProblemDetail invalidReport(InvalidReportException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid report definition", exception.getMessage());
    }

    @ExceptionHandler(ReportAccessDeniedException.class)
    ProblemDetail accessDenied(ReportAccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", exception.getMessage());
    }

    @ExceptionHandler(DatasetException.class)
    ProblemDetail dataset(DatasetException exception) {
        return switch (exception.kind()) {
            case NOT_FOUND -> problem(HttpStatus.NOT_FOUND, "Dataset not found", exception.getMessage());
            case CONFLICT -> problem(HttpStatus.CONFLICT, "Dataset conflict", exception.getMessage());
        };
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
