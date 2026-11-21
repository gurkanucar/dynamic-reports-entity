package com.gucardev.dynamicreportsentity.platform.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Sends e-mails from {@code notifications.mail.from}, only to allowed domains. Failures come back as
 * {@link MailDeliveryException}s with a code and whether to retry, so callers need not know JavaMail.
 */
@Component
public class Mailer {

    private final JavaMailSender sender;
    private final NotificationProperties properties;

    Mailer(JavaMailSender sender, NotificationProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    /** Whether e-mails may go to this address ({@code notifications.mail.allowed-recipient-domains}). */
    public boolean allows(String address) {
        return properties.mail().allows(address);
    }

    public List<String> allowedDomains() {
        return properties.mail().allowedRecipientDomains();
    }

    public void send(OutgoingMail mail) {
        List<String> all = new ArrayList<>(mail.to());
        all.addAll(mail.cc());
        all.addAll(mail.bcc());
        for (String address : all) {
            if (!allows(address)) {
                throw new MailDeliveryException("RECIPIENT_NOT_ALLOWED", "E-mails to " + address
                        + " are not allowed (notifications.mail.allowed-recipient-domains)", false, null);
            }
        }
        MimeMessage message;
        try {
            message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, !mail.attachments().isEmpty(), UTF_8.name());
            helper.setFrom(properties.mail().from());
            helper.setTo(mail.to().toArray(String[]::new));
            if (!mail.cc().isEmpty()) {
                helper.setCc(mail.cc().toArray(String[]::new));
            }
            if (!mail.bcc().isEmpty()) {
                helper.setBcc(mail.bcc().toArray(String[]::new));
            }
            helper.setSubject(mail.subject());
            helper.setText(mail.html(), true);
            for (OutgoingMail.Attachment attachment : mail.attachments()) {
                helper.addAttachment(attachment.fileName(), new ByteArrayResource(attachment.content()),
                        attachment.contentType());
            }
        } catch (MessagingException e) {
            throw new MailDeliveryException("MAIL_REJECTED", "The e-mail could not be built: " + e.getMessage(),
                    false, e);
        }
        try {
            sender.send(message);
        } catch (MailAuthenticationException e) {
            throw new MailDeliveryException("MAIL_SERVER_UNAVAILABLE", "The mail server rejected the login", true, e);
        } catch (MailSendException e) {
            if (rejectsRecipients(e)) {
                throw new MailDeliveryException("MAIL_RECIPIENT_REJECTED",
                        "The mail server rejected a recipient: " + e.getMessage(), false, e);
            }
            throw new MailDeliveryException("MAIL_SERVER_UNAVAILABLE",
                    "The e-mail could not be sent: " + e.getMessage(), true, e);
        } catch (MailException e) {
            throw new MailDeliveryException("MAIL_REJECTED", "The e-mail could not be built: " + e.getMessage(),
                    false, e);
        }
    }

    private static boolean rejectsRecipients(MailSendException exception) {
        List<Throwable> roots = new ArrayList<>(List.of(exception));
        roots.addAll(exception.getFailedMessages().values());
        for (Throwable root : roots) {
            for (Throwable cause = root; cause != null; cause = cause.getCause()) {
                if (cause instanceof SendFailedException failed && failed.getInvalidAddresses() != null
                        && failed.getInvalidAddresses().length > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
