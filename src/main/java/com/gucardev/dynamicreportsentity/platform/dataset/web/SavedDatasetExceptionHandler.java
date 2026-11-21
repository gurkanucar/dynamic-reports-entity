package com.gucardev.dynamicreportsentity.platform.dataset.web;

import com.gucardev.dynamicreportsentity.platform.api.ApiExceptionHandlerSupport;
import com.gucardev.dynamicreportsentity.platform.api.Problems;
import com.gucardev.dynamicreportsentity.platform.dataset.DatasetAccessDeniedException;
import com.gucardev.dynamicreportsentity.platform.dataset.query.InvalidDatasetException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errors of {@code /api/saved-datasets} in the shape of the REST guideline (code, detail, errors, traceId). */
@RestControllerAdvice(assignableTypes = SavedDatasetController.class)
class SavedDatasetExceptionHandler extends ApiExceptionHandlerSupport {

    @ExceptionHandler(InvalidDatasetException.class)
    ProblemDetail invalid(InvalidDatasetException exception, HttpServletRequest request) {
        return Problems.of(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_DATASET_DEFINITION", exception.getMessage(),
                request);
    }

    @ExceptionHandler(DatasetAccessDeniedException.class)
    ProblemDetail denied(DatasetAccessDeniedException exception, HttpServletRequest request) {
        return Problems.of(HttpStatus.FORBIDDEN, "DATASET_ACCESS_DENIED", exception.getMessage(), request);
    }
}
