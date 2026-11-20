package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.mail.MailDelivery;
import com.gucardev.dynamicreportsentity.platform.flow.mail.MailDeliveryRepository;
import com.gucardev.dynamicreportsentity.platform.flow.mail.MailTemplate;
import com.gucardev.dynamicreportsentity.platform.flow.mail.MailTemplateRepository;
import com.gucardev.dynamicreportsentity.platform.flow.mail.TemplateRenderer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Renders the template with the run's data and sends the e-mail. Recorded in {@link MailDelivery} right after
 * the send: a retry of a step whose mail already went out does not send it again.
 */
@Component
class MailStepHandler implements StepHandler {

    private final JavaMailSender mailSender;
    private final MailTemplateRepository templates;
    private final MailDeliveryRepository deliveries;
    private final TemplateRenderer renderer;
    private final ArtifactStore artifacts;
    private final FlowProperties properties;
    private final Clock clock;

    MailStepHandler(JavaMailSender mailSender, MailTemplateRepository templates, MailDeliveryRepository deliveries,
                    TemplateRenderer renderer, ArtifactStore artifacts, FlowProperties properties, Clock clock) {
        this.mailSender = mailSender;
        this.templates = templates;
        this.deliveries = deliveries;
        this.renderer = renderer;
        this.artifacts = artifacts;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String type() {
        return MailStep.TYPE;
    }

    @Override
    public Map<String, Object> execute(StepContext context) throws MessagingException {
        ClaimedStep claimed = context.step();
        MailStep step = (MailStep) claimed.step();
        MailDelivery earlier = deliveries.findByStepRunId(claimed.stepRunId()).orElse(null);
        if (earlier != null) {
            return output(step, earlier.getSubject(), earlier.getSentAt().toString(), true);
        }
        for (String address : concat(step.to(), step.cc())) {
            if (!properties.mail().allows(address)) {
                throw new StepException("RECIPIENT_NOT_ALLOWED", "E-mails to " + address + " are not allowed "
                        + "(flows.mail.allowed-recipient-domains)", false);
            }
        }
        MailTemplate template = templates.findById(step.templateId()).orElseThrow(() -> new StepException(
                "TEMPLATE_NOT_FOUND", "Mail template " + step.templateId() + " no longer exists", false));

        Map<String, Object> model = model(claimed);
        String subject = renderer.renderText(template.getSubject(), model);
        String body = renderer.renderHtml(template.getBodyHtml(), model);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, !step.attachments().isEmpty(), UTF_8.name());
        helper.setFrom(properties.mail().from());
        helper.setTo(step.to().toArray(String[]::new));
        if (!step.cc().isEmpty()) {
            helper.setCc(step.cc().toArray(String[]::new));
        }
        helper.setSubject(subject);
        helper.setText(body, true);
        for (String key : step.attachments()) {
            Map<String, Object> report = claimed.previousOutputs().get(key);
            Object artifactId = report == null ? null : report.get("artifactId");
            byte[] content = artifactId == null ? null : artifacts.load(((Number) artifactId).longValue());
            if (content == null) {
                throw new StepException("ATTACHMENT_NOT_AVAILABLE", "Step '" + key + "' has no file to attach",
                        false);
            }
            helper.addAttachment((String) report.get("fileName"), new ByteArrayResource(content), "text/csv");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new StepException("STEP_INTERRUPTED", "Stopped before sending", true);
        }
        mailSender.send(message);

        MailDelivery delivery = deliveries.save(new MailDelivery(claimed.stepRunId(), claimed.runId(),
                String.join(", ", concat(step.to(), step.cc())), subject, clock.instant()));
        return output(step, subject, delivery.getSentAt().toString(), false);
    }

    /** What templates see; see {@code MailTemplateService#sampleModel} for an example. */
    private Map<String, Object> model(ClaimedStep claimed) {
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("id", claimed.runId());
        run.put("trigger", claimed.trigger().name().toLowerCase());
        run.put("triggeredBy", claimed.triggeredBy());
        run.put("startedAt", claimed.runStartedAt() == null ? "" : claimed.runStartedAt().toString());
        run.put("date", LocalDate.ofInstant(clock.instant(), ZoneId.systemDefault()).toString());
        return Map.of(
                "flow", Map.of("id", claimed.flowId(), "name", claimed.flowName()),
                "run", run,
                "steps", claimed.previousOutputs());
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
