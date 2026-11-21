package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.spi.StepCheck;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepReference;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepType;
import com.gucardev.dynamicreportsentity.platform.notification.MailTemplateService;
import com.gucardev.dynamicreportsentity.platform.notification.Mailer;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Set;

/** The step types the engine brings itself; other modules add theirs the same way. */
final class CoreStepTypes {

    static final String MAIL_TEMPLATE = "mail-template";

    private CoreStepTypes() {
    }

    @Component
    static class Mail implements StepType<MailStep> {

        private final MailTemplateService templates;
        private final Mailer mailer;

        Mail(MailTemplateService templates, Mailer mailer) {
            this.templates = templates;
            this.mailer = mailer;
        }

        @Override
        public String name() {
            return MailStep.TYPE;
        }

        @Override
        public Class<MailStep> stepClass() {
            return MailStep.class;
        }

        @Override
        public void validate(MailStep step, StepCheck check) {
            if (!templates.exists(step.templateId())) {
                check.problem("templateId", "TEMPLATE_NOT_FOUND",
                        "Mail template " + step.templateId() + " does not exist.");
            }
            recipients(step.to(), "to", check);
            recipients(step.cc(), "cc", check);
            for (int i = 0; i < step.attachments().size(); i++) {
                String key = step.attachments().get(i);
                if (!check.stepsBefore().contains(key) || !check.producesFiles(key)) {
                    check.problem("attachments[" + i + "]", "UNKNOWN_ATTACHMENT",
                            "'" + key + "' is not a dataset step that runs before this one.");
                }
            }
        }

        private void recipients(List<String> addresses, String field, StepCheck check) {
            for (int i = 0; i < addresses.size(); i++) {
                if (!mailer.allows(addresses.get(i))) {
                    check.problem(field + "[" + i + "]", "RECIPIENT_NOT_ALLOWED",
                            "E-mails may only go to " + String.join(", ", mailer.allowedDomains()) + ".");
                }
            }
        }

        @Override
        public Set<StepReference> references(MailStep step) {
            return Set.of(StepReference.of(MAIL_TEMPLATE, step.templateId()));
        }
    }

    @Component
    static class Condition implements StepType<ConditionStep> {

        @Override
        public String name() {
            return ConditionStep.TYPE;
        }

        @Override
        public Class<ConditionStep> stepClass() {
            return ConditionStep.class;
        }

        @Override
        public void validate(ConditionStep step, StepCheck check) {
            check.expression("expression", step.expression());
        }
    }

    @Component
    static class Filter implements StepType<FilterStep> {

        @Override
        public String name() {
            return FilterStep.TYPE;
        }

        @Override
        public Class<FilterStep> stepClass() {
            return FilterStep.class;
        }

        @Override
        public void validate(FilterStep step, StepCheck check) {
            check.expression("expression", step.expression());
        }
    }

    @Component
    static class Webhook implements StepType<WebhookStep> {

        private final FlowProperties properties;

        Webhook(FlowProperties properties) {
            this.properties = properties;
        }

        @Override
        public String name() {
            return WebhookStep.TYPE;
        }

        @Override
        public Class<WebhookStep> stepClass() {
            return WebhookStep.class;
        }

        @Override
        public void validate(WebhookStep step, StepCheck check) {
            URI url;
            try {
                url = URI.create(step.url().strip());
            } catch (IllegalArgumentException e) {
                check.problem("url", "INVALID_URL", "Not a valid URL.");
                return;
            }
            if (!"http".equalsIgnoreCase(url.getScheme()) && !"https".equalsIgnoreCase(url.getScheme())) {
                check.problem("url", "INVALID_URL", "Only http and https URLs can be called.");
            } else if (!properties.webhook().allows(url.getHost())) {
                check.problem("url", "WEBHOOK_HOST_NOT_ALLOWED", "Calls to " + url.getHost()
                        + " are not allowed (flows.webhook.allowed-hosts).");
            }
            if (step.body() != null) {
                check.template("body", step.body());
            }
        }
    }

    @Component
    static class Kafka implements StepType<KafkaPublishStep> {

        private final FlowProperties properties;

        Kafka(FlowProperties properties) {
            this.properties = properties;
        }

        @Override
        public String name() {
            return KafkaPublishStep.TYPE;
        }

        @Override
        public Class<KafkaPublishStep> stepClass() {
            return KafkaPublishStep.class;
        }

        @Override
        public void validate(KafkaPublishStep step, StepCheck check) {
            if (!properties.kafka().enabled()) {
                check.problem("", "KAFKA_DISABLED", "Kafka is not enabled (flows.kafka.enabled).");
            } else if (!properties.kafka().allows(step.topic())) {
                check.problem("topic", "TOPIC_NOT_ALLOWED", "Publishing to " + step.topic()
                        + " is not allowed (flows.kafka.allowed-topics).");
            }
            check.template("payload", step.payload());
            if (step.messageKey() != null) {
                check.template("messageKey", step.messageKey());
            }
        }
    }
}
