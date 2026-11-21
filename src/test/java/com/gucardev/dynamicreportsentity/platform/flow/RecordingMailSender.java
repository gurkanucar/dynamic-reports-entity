package com.gucardev.dynamicreportsentity.platform.flow;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Replaces the SMTP sender in every test context. State is static: test contexts with different settings run
 * their own flow engines against the same in-memory database (a small cluster), and any of them may send.
 */
@Component
@Primary
public class RecordingMailSender implements JavaMailSender {

    public static final List<MimeMessage> SENT = new CopyOnWriteArrayList<>();
    /** The next N sends fail like an unreachable mail server. */
    public static final AtomicInteger FAILURES = new AtomicInteger();
    /** Sends hang until this is false again or the thread is interrupted. */
    public static volatile boolean block;
    public static final AtomicInteger BLOCKED = new AtomicInteger();
    /** Every send takes this long (to keep several instances busy at once). */
    public static volatile long delayMillis;

    public static void reset() {
        SENT.clear();
        FAILURES.set(0);
        block = false;
        delayMillis = 0;
    }

    @Override
    public MimeMessage createMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void send(MimeMessage... messages) throws MailException {
        if (FAILURES.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new MailSendException("Connection refused (test)");
        }
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MailSendException("Interrupted (test)", e);
            }
        }
        if (block) {
            BLOCKED.incrementAndGet();
            try {
                while (block) {
                    Thread.sleep(20);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MailSendException("Interrupted (test)", e);
            } finally {
                BLOCKED.decrementAndGet();
            }
        }
        SENT.addAll(List.of(messages));
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) throws MailException {
        throw new UnsupportedOperationException();
    }

    // ---- reading sent messages ----------------------------------------------------------------------------

    public static String html(MimeMessage message) {
        try {
            return html(message.getContent());
        } catch (IOException | MessagingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String html(Object content) throws MessagingException, IOException {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                if (!Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                    String html = html(part.getContent());
                    if (html != null) {
                        return html;
                    }
                }
            }
        }
        return null;
    }

    /** File name to text content. */
    public static Map<String, String> attachments(MimeMessage message) {
        Map<String, String> files = new LinkedHashMap<>();
        attachmentBytes(message).forEach((name, bytes) -> files.put(name, new String(bytes, UTF_8)));
        return files;
    }

    /** Attachments as they are (Excel files are binary). */
    public static Map<String, byte[]> attachmentBytes(MimeMessage message) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try {
            collect(message.getContent(), files);
        } catch (IOException | MessagingException e) {
            throw new IllegalStateException(e);
        }
        return files;
    }

    private static void collect(Object content, Map<String, byte[]> files) throws MessagingException, IOException {
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                    try (InputStream in = part.getInputStream()) {
                        files.put(part.getFileName(), in.readAllBytes());
                    }
                } else {
                    collect(part.getContent(), files);
                }
            }
        }
    }
}
