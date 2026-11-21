package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.api.Ids;
import java.util.UUID;
import com.gucardev.dynamicreportsentity.platform.flow.spi.ClaimedStep;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepResult;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepException;
import com.gucardev.dynamicreportsentity.platform.flow.spi.RunData;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepHandler;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepContext;
import com.gucardev.dynamicreportsentity.platform.notification.MailDeliveryException;
import com.gucardev.dynamicreportsentity.platform.notification.MailTemplateService;
import com.gucardev.dynamicreportsentity.platform.notification.Mailer;
import com.gucardev.dynamicreportsentity.platform.notification.OutgoingMail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the template with the run's data and sends the e-mail through the notification module. Recorded in
 * {@link MailDelivery} right after the send: a retry of a step whose mail already went out does not send it again.
 */
@Slf4j
@Component
class MailStepHandler implements StepHandler {

    private final MailTemplateService templates;
    private final Mailer mailer;
    private final MailDeliveryRepository deliveries;
    private final ArtifactStore artifacts;
    private final Clock clock;

    MailStepHandler(MailTemplateService templates, Mailer mailer, MailDeliveryRepository deliveries,
                    ArtifactStore artifacts, Clock clock) {
        this.templates = templates;
        this.mailer = mailer;
        this.deliveries = deliveries;
        this.artifacts = artifacts;
        this.clock = clock;
    }

    @Override
    public String type() {
        return MailStep.TYPE;
    }

    @Override
    public StepResult execute(StepContext context) {
        ClaimedStep claimed = context.step();
        MailStep step = (MailStep) claimed.step();
        MailDelivery earlier = deliveries.findByStepRunId(claimed.stepRunId()).orElse(null);
        if (earlier != null) {
            return StepResult.done(output(step, earlier.getSubject(), earlier.getSentAt().toString(),
                    earlier.getBodyFileId(), true));
        }
        MailTemplateService.RenderedMail rendered = templates.render(step.templateId(), RunData.of(claimed, clock))
                .orElseThrow(() -> new StepException("TEMPLATE_NOT_FOUND",
                        "Mail template " + step.templateId() + " no longer exists", false));
        List<OutgoingMail.Attachment> attachments = new ArrayList<>();
        for (String key : step.attachments()) {
            Map<String, Object> dataset = claimed.previousOutputs().get(key);
            Object artifactId = dataset == null ? null : dataset.get("artifactId");
            byte[] content = artifactId == null ? null : artifacts.load(Ids.parse(artifactId));
            if (content == null) {
                throw new StepException("ATTACHMENT_NOT_AVAILABLE", "Step '" + key + "' has no file to attach",
                        false);
            }
            Object contentType = dataset.get("contentType");   // missing in runs from before Excel files: CSV
            attachments.add(new OutgoingMail.Attachment((String) dataset.get("fileName"),
                    contentType == null ? "text/csv" : contentType.toString(), content));
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new StepException("STEP_INTERRUPTED", "Stopped before sending", true);
        }
        UUID bodyFileId = keepBody(context, step.key(), rendered.html());
        try {
            mailer.send(new OutgoingMail(step.to(), step.cc(), List.of(), rendered.subject(), rendered.html(),
                    attachments));
        } catch (MailDeliveryException e) {
            throw new StepException(e.code(), e.getMessage(), e.retryable());
        }
        MailDelivery delivery = deliveries.save(new MailDelivery(claimed.stepRunId(), claimed.runId(),
                String.join(", ", concat(step.to(), step.cc())), rendered.subject(), clock.instant(), bodyFileId));
        return StepResult.done(output(step, rendered.subject(), delivery.getSentAt().toString(), bodyFileId, false));
    }

    /**
     * Keeps the HTML as it is sent, so the run shows what recipients got; kept as long as other run files
     * ({@code flows.file-retention}). Not keeping it never stops the mail.
     */
    private static UUID keepBody(StepContext context, String stepKey, String html) {
        Path file = null;
        try {
            file = Files.createTempFile("mail-", ".html");
            Files.writeString(file, html, StandardCharsets.UTF_8);
            return context.storeFile(stepKey + "-mail.html", "text/html", file).id();
        } catch (IOException | RuntimeException e) {
            log.warn("Could not keep the body of the mail of step {}: {}", stepKey, e.getMessage());
            return null;
        } finally {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                    // temp file; the OS cleans it up
                }
            }
        }
    }

    private static Map<String, Object> output(MailStep step, String subject, String sentAt, UUID bodyFileId,
                                              boolean alreadySent) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("to", step.to());
        output.put("cc", step.cc());
        output.put("subject", subject);
        output.put("sentAt", sentAt);
        output.put("attachmentCount", step.attachments().size());
        output.put("sentByEarlierAttempt", alreadySent);
        output.put("bodyFileId", bodyFileId);
        return output;
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}
