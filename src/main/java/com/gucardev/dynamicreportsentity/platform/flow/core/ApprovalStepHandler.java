package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.approval.ApprovalGroup;
import com.gucardev.dynamicreportsentity.platform.flow.approval.ApprovalGroupRepository;
import com.gucardev.dynamicreportsentity.platform.flow.approval.ApprovalRequest;
import com.gucardev.dynamicreportsentity.platform.flow.approval.ApprovalRequestRepository;
import com.gucardev.dynamicreportsentity.platform.flow.mail.TemplateRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Opens an approval request and lets the step wait for it (no thread, no lease). The decision comes through
 * {@code RunStore.decide}, a timeout through the engine's poll. Group members with an e-mail address get a
 * notification with a link to the request; a failed notification does not fail the step.
 */
@Slf4j
@Component
class ApprovalStepHandler implements StepHandler {

    private final ApprovalRequestRepository requests;
    private final ApprovalGroupRepository groups;
    private final TemplateRenderer renderer;
    private final JavaMailSender mail;
    private final FlowProperties properties;
    private final Clock clock;

    ApprovalStepHandler(ApprovalRequestRepository requests, ApprovalGroupRepository groups, TemplateRenderer renderer,
                        JavaMailSender mail, FlowProperties properties, Clock clock) {
        this.requests = requests;
        this.groups = groups;
        this.renderer = renderer;
        this.mail = mail;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String type() {
        return ApprovalStep.TYPE;
    }

    @Override
    public StepResult execute(StepContext context) {
        ClaimedStep claimed = context.step();
        ApprovalStep step = (ApprovalStep) claimed.step();
        ApprovalRequest request = requests.findByStepRunId(claimed.stepRunId()).orElse(null);
        if (request == null) {   // an earlier attempt may have opened it already
            ApprovalGroup group = groups.findById(step.groupId()).orElseThrow(() -> new StepException(
                    "APPROVAL_GROUP_NOT_FOUND", "Approval group " + step.groupId() + " no longer exists", false));
            if (group.getMembers().isEmpty()) {
                throw new StepException("APPROVAL_GROUP_EMPTY", "Approval group '" + group.getName()
                        + "' has no members", false);
            }
            Map<String, Object> data = RunData.of(claimed, clock);
            Instant now = clock.instant();
            request = requests.save(new ApprovalRequest(claimed.runId(), claimed.stepRunId(), claimed.flowId(),
                    claimed.flowName(), step.key(), group, renderer.renderText(step.title(), data),
                    step.description() == null ? null : renderer.renderPlain(step.description(), data),
                    claimed.triggeredBy(), claimed.runAs(), Boolean.TRUE.equals(step.allowSelfApproval()), now,
                    now.plus(Duration.ofHours(step.timeoutHoursOrDefault()))));
            if (!Boolean.FALSE.equals(step.notifyMembers())) {
                notifyMembers(group, request);
            }
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("requestId", request.getId());
        output.put("groupId", request.getGroupId());
        output.put("groupName", request.getGroupName());
        output.put("title", request.getTitle());
        output.put("dueAt", request.getDueAt().toString());
        return StepResult.waitUntil(output, request.getDueAt());
    }

    private void notifyMembers(ApprovalGroup group, ApprovalRequest request) {
        List<String> addresses = group.getMembers().stream().map(ApprovalGroup.Member::email)
                .filter(email -> email != null && !email.isBlank() && properties.mail().allows(email)).toList();
        if (addresses.isEmpty()) {
            return;
        }
        try {
            var message = mail.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, UTF_8.name());
            helper.setFrom(properties.mail().from());
            helper.setBcc(addresses.toArray(String[]::new));
            helper.setSubject("Onay bekliyor: " + request.getTitle());
            String link = properties.uiBaseUrl() + "/approvals?request=" + request.getId();
            helper.setText("<p><b>" + HtmlUtils.htmlEscape(request.getTitle()) + "</b></p>"
                    + (request.getDescription() == null ? ""
                    : "<p style=\"white-space:pre-line\">" + HtmlUtils.htmlEscape(request.getDescription()) + "</p>")
                    + "<p>Akış: " + HtmlUtils.htmlEscape(request.getFlowName()) + " · Son tarih: "
                    + request.getDueAt() + "</p><p><a href=\"" + HtmlUtils.htmlEscape(link) + "\">Onayla / reddet</a></p>",
                    true);
            mail.send(message);
        } catch (Exception e) {
            log.warn("Could not notify approval group '{}' about request {}: {}", group.getName(), request.getId(),
                    e.getMessage());
        }
    }
}
