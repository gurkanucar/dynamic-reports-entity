package com.gucardev.dynamicreportsentity.platform.flow.web;

import com.gucardev.dynamicreportsentity.platform.flow.core.FlowService.FlowInput;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowStep;
import com.gucardev.dynamicreportsentity.platform.flow.core.ScheduleService.ScheduleInput;
import com.gucardev.dynamicreportsentity.platform.flow.mail.MailTemplateService.TemplateInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** Request bodies of the flow API, with their field rules. */
final class FlowRequests {

    private FlowRequests() {
    }

    record FlowRequest(@NotBlank @Size(max = 100) String name,
                       @Size(max = 1000) String description,
                       @NotEmpty @Size(max = 20) List<@NotNull @Valid FlowStep> steps) {

        FlowInput input() {
            return new FlowInput(name, description, steps);
        }
    }

    /**
     * @param cron     5 fields ({@code 0 8 * * MON-FRI}), 6 with seconds, or a macro such as {@code @daily}
     * @param timeZone IANA zone, e.g. {@code Europe/Istanbul}
     */
    record ScheduleRequest(@NotBlank @Size(max = 100) String cron,
                           @NotBlank @Size(max = 60) String timeZone,
                           @NotNull Boolean enabled) {

        ScheduleInput input() {
            return new ScheduleInput(cron, timeZone, enabled);
        }
    }

    record TemplateRequest(@NotBlank @Size(max = 100) String name,
                           @Size(max = 1000) String description,
                           @NotBlank @Size(max = 500) String subject,
                           @NotBlank @Size(max = 200_000) String bodyHtml) {

        TemplateInput input() {
            return new TemplateInput(name, description, subject, bodyHtml);
        }
    }

    /** @param model data to render with; null = a sample run with one report step named {@code report} */
    record PreviewRequest(@NotBlank @Size(max = 500) String subject,
                          @NotBlank @Size(max = 200_000) String bodyHtml,
                          Map<String, Object> model) {
    }
}
