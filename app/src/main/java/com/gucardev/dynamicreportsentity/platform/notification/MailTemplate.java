package com.gucardev.dynamicreportsentity.platform.notification;

import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A Mustache template for e-mails: subject (plain text) and HTML body. Shared: every user may use any
 * template; only its owner or a dataset admin may change it.
 */
@Entity
@Table(name = "notification_mail_template", uniqueConstraints = @UniqueConstraint(name = "uk_mail_template_name",
        columnNames = "name"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailTemplate {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Version
    private long version;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(name = "body_html", nullable = false, length = 200_000)
    private String bodyHtml;

    @Column(nullable = false, length = 100)
    private String owner;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false, length = 100)
    private String updatedBy;

    MailTemplate(String owner, Instant now) {
        this.owner = owner;
        this.createdAt = now;
    }

    void define(String name, String description, String subject, String bodyHtml, String editor, Instant now) {
        this.name = name;
        this.description = description;
        this.subject = subject;
        this.bodyHtml = bodyHtml;
        this.updatedBy = editor;
        this.updatedAt = now;
    }
}
