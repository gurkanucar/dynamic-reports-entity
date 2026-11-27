package com.gucardev.dynamicreportsentity.platform.notification;

import java.util.List;

/**
 * An e-mail to send. {@code bcc} is for notifying a group without showing the addresses to each other.
 *
 * @param html body as HTML (already escaped where it holds user data)
 */
public record OutgoingMail(List<String> to, List<String> cc, List<String> bcc, String subject, String html,
                           List<Attachment> attachments) {

    public OutgoingMail {
        to = to == null ? List.of() : List.copyOf(to);
        cc = cc == null ? List.of() : List.copyOf(cc);
        bcc = bcc == null ? List.of() : List.copyOf(bcc);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public record Attachment(String fileName, String contentType, byte[] content) {
    }
}
