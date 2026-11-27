package com.gucardev.dynamicreportsentity.platform.notification;

import com.gucardev.dynamicreportsentity.platform.api.ApiException;

import java.util.List;

/** An {@link ApiException} of the notification API. */
public class NotificationException extends ApiException {

    public NotificationException(Kind kind, String code, String message) {
        super(kind, code, message);
    }

    public static NotificationException invalid(List<FieldProblem> errors) {
        return new NotificationException(errors, summary(errors));
    }

    private NotificationException(List<FieldProblem> errors, String message) {
        super(Kind.UNPROCESSABLE, "VALIDATION_FAILED", message, errors);
    }
}
