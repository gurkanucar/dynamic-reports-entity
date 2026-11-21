package com.gucardev.dynamicreportsentity.platform.approval;

import com.gucardev.dynamicreportsentity.platform.notification.Mailer;
import com.gucardev.dynamicreportsentity.platform.notification.OutgoingMail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

/**
 * E-mails the members of a group (those with an address) about a new request, after it was committed. A failed
 * notification is logged, not retried: the request is in the members' list in the UI anyway.
 */
@Slf4j
@Component
class ApprovalNotifier {

    private final ApprovalRequestRepository requests;
    private final ApprovalGroupRepository groups;
    private final Mailer mailer;
    private final ApprovalProperties properties;

    ApprovalNotifier(ApprovalRequestRepository requests, ApprovalGroupRepository groups, Mailer mailer,
                     ApprovalProperties properties) {
        this.requests = requests;
        this.groups = groups;
        this.mailer = mailer;
        this.properties = properties;
    }

    @ApplicationModuleListener
    void on(ApprovalRequested event) {
        if (!event.notifyMembers()) {
            return;
        }
        ApprovalRequest request = requests.findById(event.requestId()).orElse(null);
        ApprovalGroup group = request == null ? null : groups.findById(request.getGroupId()).orElse(null);
        if (group == null) {
            return;
        }
        List<String> addresses = group.getMembers().stream().map(ApprovalGroup.Member::email)
                .filter(email -> email != null && !email.isBlank() && mailer.allows(email)).toList();
        if (addresses.isEmpty()) {
            return;
        }
        String link = properties.uiBaseUrl() + "/approvals?request=" + request.getId();
        String html = "<p><b>" + HtmlUtils.htmlEscape(request.getTitle()) + "</b></p>"
                + (request.getDescription() == null ? ""
                : "<p style=\"white-space:pre-line\">" + HtmlUtils.htmlEscape(request.getDescription()) + "</p>")
                + "<p>" + HtmlUtils.htmlEscape(request.getSubject()) + " · Son tarih: " + request.getDueAt() + "</p>"
                + "<p><a href=\"" + HtmlUtils.htmlEscape(link) + "\">Onayla / reddet</a></p>";
        try {
            mailer.send(new OutgoingMail(List.of(), List.of(), addresses, "Onay bekliyor: " + request.getTitle(), html,
                    List.of()));
        } catch (RuntimeException e) {
            log.warn("Could not notify approval group '{}' about request {}: {}", group.getName(), request.getId(),
                    e.getMessage());
        }
    }
}
