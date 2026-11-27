package com.gucardev.dynamicreportsentity.platform.notification.web;

import java.util.UUID;
import com.gucardev.dynamicreportsentity.platform.notification.MailTemplate;
import com.gucardev.dynamicreportsentity.platform.notification.MailTemplateService.TemplateInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.time;

/** Request and response bodies of the mail template API. */
final class NotificationApi {

    private NotificationApi() {
    }

    record UserRef(String username) {
    }

    record TemplateRequest(@NotBlank @Size(max = 100) String name,
                           @Size(max = 1000) String description,
                           @NotBlank @Size(max = 500) String subject,
                           @NotBlank @Size(max = 200_000) String bodyHtml) {

        TemplateInput input() {
            return new TemplateInput(name, description, subject, bodyHtml);
        }
    }

    /** @param model data to render with; null = a sample run with one dataset step named {@code dataset} */
    record PreviewRequest(@NotBlank @Size(max = 500) String subject,
                          @NotBlank @Size(max = 200_000) String bodyHtml,
                          Map<String, Object> model) {
    }

    record TemplateSummary(UUID id, String name, String description, UserRef owner, long version, String updatedAt) {
    }

    record TemplateDetail(UUID id, String name, String description, String subject, String bodyHtml, UserRef owner,
                          long version, String createdAt, String updatedAt, UserRef updatedBy) {
    }

    static TemplateSummary summary(MailTemplate template) {
        return new TemplateSummary(template.getId(), template.getName(), template.getDescription(),
                new UserRef(template.getOwner()), template.getVersion(), time(template.getUpdatedAt()));
    }

    static TemplateDetail detail(MailTemplate template) {
        return new TemplateDetail(template.getId(), template.getName(), template.getDescription(),
                template.getSubject(), template.getBodyHtml(), new UserRef(template.getOwner()),
                template.getVersion(), time(template.getCreatedAt()), time(template.getUpdatedAt()),
                new UserRef(template.getUpdatedBy()));
    }
}
