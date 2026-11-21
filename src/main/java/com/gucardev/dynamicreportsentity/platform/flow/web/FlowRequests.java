package com.gucardev.dynamicreportsentity.platform.flow.web;

import com.gucardev.dynamicreportsentity.platform.flow.approval.ApprovalGroup;
import com.gucardev.dynamicreportsentity.platform.flow.core.ApprovalService;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowDefinition;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowEdge;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowService.FlowInput;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowTrigger;
import com.gucardev.dynamicreportsentity.platform.flow.core.TriggerService;
import com.gucardev.dynamicreportsentity.platform.flow.core.FlowStep;
import com.gucardev.dynamicreportsentity.platform.flow.core.ScheduleService.ScheduleInput;
import com.gucardev.dynamicreportsentity.platform.flow.mail.MailTemplateService.TemplateInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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

    /**
     * @param edges  connections between steps (empty = in order); see {@code FlowGraph}
     * @param layout canvas positions of the steps by key (UI only)
     */
    record FlowRequest(@NotBlank @Size(max = 100) String name,
                       @Size(max = 1000) String description,
                       @NotEmpty @Size(max = 30) List<@NotNull @Valid FlowStep> steps,
                       @Size(max = 60) List<@NotNull @Valid FlowEdge> edges,
                       @Size(max = 30) Map<String, FlowDefinition.Position> layout) {

        FlowInput input() {
            return new FlowInput(name, description, steps, edges == null ? List.of() : edges,
                    layout == null ? Map.of() : layout);
        }
    }

    /** @param filter optional JSON Logic over {@code {trigger}} */
    record TriggerRequest(@NotNull FlowTrigger.Type type, @NotNull Boolean enabled, @Size(max = 249) String topic,
                          Object filter) {

        TriggerService.TriggerInput input() {
            return new TriggerService.TriggerInput(type, enabled, topic, filter);
        }
    }

    record GroupRequest(@NotBlank @Size(max = 100) String name,
                        @Size(max = 1000) String description,
                        @Size(max = 200) List<@NotNull @Valid MemberRequest> members) {

        ApprovalService.GroupInput input() {
            return new ApprovalService.GroupInput(name, description, members == null ? List.of()
                    : members.stream().map(m -> new ApprovalGroup.Member(m.username(), m.email())).toList());
        }
    }

    record MemberRequest(@NotBlank @Size(max = 100) String username, @Email @Size(max = 254) String email) {
    }

    record DecisionRequest(@Size(max = 1000) String comment) {
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
