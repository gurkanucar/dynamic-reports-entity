package com.gucardev.dynamicreportsentity.platform.flow.core;

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
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the template with the run's data and sends the e-mail through the notification module. Recorded in
 * {@link MailDelivery} right after the send: a retry of a step whose mail already went out does not send it again.
 */
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
            return StepResult.done(output(step, earlier.getSubject(), earlier.getSentAt().toString(), true));
        }
        MailTemplateService.RenderedMail rendered = templates.render(step.templateId(), RunData.of(claimed, clock))
                .orElseThrow(() -> new StepException("TEMPLATE_NOT_FOUND",
                        "Mail template " + step.templateId() + " no longer exists", false));
        List<OutgoingMail.Attachment> attachments = new ArrayList<>();
        for (String key : step.attachments()) {
            Map<String, Object> report = claimed.previousOutputs().get(key);
            Object artifactId = report == null ? null : report.get("artifactId");
            byte[] content = artifactId == null ? null : artifacts.load(((Number) artifactId).longValue());
            if (content == null) {
                throw new StepException("ATTACHMENT_NOT_AVAILABLE", "Step '" + key + "' has no file to attach",
                        false);
            }
            attachments.add(new OutgoingMail.Attachment((String) report.get("fileName"), "text/csv", content));
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new StepException("STEP_INTERRUPTED", "Stopped before sending", true);
        }
        try {
            mailer.send(new OutgoingMail(step.to(), step.cc(), List.of(), rendered.subject(), rendered.html(),
                    attachments));
        } catch (MailDeliveryException e) {
            throw new StepException(e.code(), e.getMessage(), e.retryable());
        }
        MailDelivery delivery = deliveries.save(new MailDelivery(claimed.stepRunId(), claimed.runId(),
                String.join(", ", concat(step.to(), step.cc())), rendered.subject(), clock.instant()));
        return StepResult.done(output(step, rendered.subject(), delivery.getSentAt().toString(), false));
    }

    private static Map<String, Object> output(MailStep step, String subject, String sentAt, boolean alreadySent) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("to", step.to());
        output.put("cc", step.cc());
        output.put("subject", subject);
        output.put("sentAt", sentAt);
        output.put("attachmentCount", step.attachments().size());
        output.put("sentByEarlierAttempt", alreadySent);
        return output;
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }
}
