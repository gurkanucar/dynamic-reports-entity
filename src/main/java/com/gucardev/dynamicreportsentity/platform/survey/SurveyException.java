package com.gucardev.dynamicreportsentity.platform.survey;

import com.gucardev.dynamicreportsentity.platform.api.ApiException;

/** An {@link ApiException} of the survey API (codes in {@code api/errors.yaml}). */
public class SurveyException extends ApiException {

    SurveyException(Kind kind, String code, String message) {
        super(kind, code, message);
    }

    static SurveyException notFound(long id) {
        return new SurveyException(Kind.NOT_FOUND, "SURVEY_NOT_FOUND", "There is no survey " + id + ".");
    }
}
