package com.gucardev.dynamicreportsentity.platform.notification;

/**
 * An e-mail that could not be sent, with a stable code: {@code RECIPIENT_NOT_ALLOWED}, {@code MAIL_RECIPIENT_REJECTED},
 * {@code MAIL_REJECTED} (cannot be built) or {@code MAIL_SERVER_UNAVAILABLE}. {@link #retryable()}: whether sending
 * the same mail again later may work.
 */
public class MailDeliveryException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public MailDeliveryException(String code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
