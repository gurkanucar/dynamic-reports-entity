package com.gucardev.dynamicreportsentity.platform.approval;

import com.gucardev.dynamicreportsentity.platform.notification.Mailer;
import com.gucardev.dynamicreportsentity.platform.notification.OutgoingMail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import com.gucardev.dynamicreportsentity.platform.notification.CssInliner;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
        String html = CssInliner.inline(html(request, group));
        try {
            mailer.send(new OutgoingMail(List.of(), List.of(), addresses, "Onay bekliyor: " + request.getTitle(), html,
                    List.of()));
        } catch (RuntimeException e) {
            log.warn("Could not notify approval group '{}' about request {}: {}", group.getName(), request.getId(),
                    e.getMessage());
        }
    }

    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.of("tr"))
            .withZone(ZoneId.of("Europe/Istanbul"));

    /**
     * The request as a card with "Onayla" and "Reddet" buttons. They open the request in the UI with that decision
     * preselected; deciding still takes a click there, because mail scanners open links on their own.
     */
    private String html(ApprovalRequest request, ApprovalGroup group) {
        String base = properties.uiBaseUrl() + "/approvals?request=" + request.getId();
        String who = request.getOnBehalfOf() != null ? request.getOnBehalfOf() : request.getRequestedBy();
        String description = request.getDescription() == null || request.getDescription().isBlank() ? ""
                : "<div class=\"description\">" + escape(request.getDescription()) + "</div>";
        return """
                <style>
                  body { margin: 0; padding: 24px 0; background: #f4f4f5; }
                  .wrap { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 10px;
                    border-top: 4px solid #dc2626; padding: 28px; font-family: -apple-system, Segoe UI, Roboto,
                    Helvetica, Arial, sans-serif; font-size: 14px; line-height: 1.55; color: #18181b; }
                  .eyebrow { margin: 0 0 6px; color: #dc2626; font-size: 12px; font-weight: 600; letter-spacing: .04em;
                    text-transform: uppercase; }
                  h1 { margin: 0 0 16px; font-size: 20px; line-height: 1.3; }
                  .description { white-space: pre-line; background: #f4f4f5; border-radius: 8px; padding: 12px 14px;
                    margin: 0 0 16px; }
                  table.details { width: 100%%; border-collapse: collapse; margin: 0 0 24px; font-size: 13px; }
                  table.details td { padding: 6px 0; border-bottom: 1px solid #f4f4f5; vertical-align: top; }
                  table.details td.label { width: 120px; color: #71717a; }
                  .buttons { margin: 0 0 20px; }
                  a.approve { display: inline-block; background: #16a34a; color: #ffffff; text-decoration: none;
                    font-weight: 600; padding: 11px 22px; border-radius: 8px; margin: 0 8px 8px 0; }
                  a.reject { display: inline-block; background: #ffffff; color: #dc2626; text-decoration: none;
                    font-weight: 600; padding: 10px 21px; border-radius: 8px; border: 1px solid #fca5a5;
                    margin: 0 8px 8px 0; }
                  .note { color: #71717a; font-size: 12px; margin: 0; }
                  .footer { margin-top: 20px; padding-top: 12px; border-top: 1px solid #e4e4e7; color: #a1a1aa;
                    font-size: 12px; }
                </style>
                <div class="wrap">
                  <p class="eyebrow">Onayınız bekleniyor</p>
                  <h1>%s</h1>
                  %s
                  <table class="details">
                    <tr><td class="label">Kaynak</td><td>%s</td></tr>
                    <tr><td class="label">Talep eden</td><td>%s</td></tr>
                    <tr><td class="label">Onay grubu</td><td>%s</td></tr>
                    <tr><td class="label">Son tarih</td><td><b>%s</b></td></tr>
                  </table>
                  <div class="buttons">
                    <a class="approve" href="%s">✓ Onayla</a>
                    <a class="reject" href="%s">✕ Reddet</a>
                  </div>
                  <p class="note">Düğmeler talebi uygulamada açar; kararınızı orada, isterseniz bir yorumla verirsiniz.
                    Son tarihe kadar karar verilmezse talep zaman aşımına uğrar.</p>
                  <p class="footer">Bu e-postayı “%s” grubunun üyesi olduğunuz için aldınız.</p>
                </div>
                """.formatted(escape(request.getTitle()), description, escape(request.getSubject()), escape(who),
                escape(group.getName()), DUE.format(request.getDueAt()), escape(base + "&decision=approve"),
                escape(base + "&decision=reject"), escape(group.getName()));
    }

    private static String escape(String text) {
        return text == null ? "" : HtmlUtils.htmlEscape(text);
    }
}
