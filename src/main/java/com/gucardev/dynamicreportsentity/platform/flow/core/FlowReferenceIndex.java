package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowReferences;
import com.gucardev.dynamicreportsentity.platform.notification.MailTemplateUsage;
import org.springframework.stereotype.Component;

/**
 * Answers "does a flow use this?" from the references stored with each flow. Also tells the notification module
 * which mail templates are in use (the adapters of other modules do the same for theirs).
 */
@Component
class FlowReferenceIndex implements FlowReferences, MailTemplateUsage {

    private final FlowRepository flows;

    FlowReferenceIndex(FlowRepository flows) {
        this.flows = flows;
    }

    @Override
    public boolean isReferenced(String kind, String id) {
        return flows.existsReference(kind, id);
    }

    @Override
    public boolean usesTemplate(long templateId) {
        return isReferenced(CoreStepTypes.MAIL_TEMPLATE, Long.toString(templateId));
    }
}
