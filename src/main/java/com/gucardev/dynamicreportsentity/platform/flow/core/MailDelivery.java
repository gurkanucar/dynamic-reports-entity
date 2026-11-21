package com.gucardev.dynamicreportsentity.platform.flow.core;

import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Record of a sent flow e-mail; one per step run, so a retried step does not send it again. */
@Entity
@Table(name = "flow_mail_delivery", uniqueConstraints = @UniqueConstraint(name = "uk_mail_delivery_step",
        columnNames = "step_run_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailDelivery {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "step_run_id", nullable = false)
    private UUID stepRunId;

    @Column(nullable = false)
    private UUID runId;

    @Column(nullable = false, length = 4000)
    private String recipients;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(nullable = false)
    private Instant sentAt;

    /** The sent HTML as a run file ({@link RunArtifact}); null if it was not kept. */
    @Column(name = "body_file_id")
    private UUID bodyFileId;

    public MailDelivery(UUID stepRunId, UUID runId, String recipients, String subject, Instant sentAt,
                        UUID bodyFileId) {
        this.bodyFileId = bodyFileId;
        this.stepRunId = stepRunId;
        this.runId = runId;
        this.recipients = recipients.length() > 4000 ? recipients.substring(0, 4000) : recipients;
        this.subject = subject.length() > 500 ? subject.substring(0, 500) : subject;
        this.sentAt = sentAt;
    }
}
