package com.gucardev.dynamicreportsentity.platform.flowsurvey;

import com.gucardev.dynamicreportsentity.platform.api.ApiException;
import com.gucardev.dynamicreportsentity.platform.flow.spi.ClaimedStep;
import com.gucardev.dynamicreportsentity.platform.flow.spi.RunData;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepContext;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepException;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepHandler;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepResult;
import com.gucardev.dynamicreportsentity.platform.notification.CssInliner;
import com.gucardev.dynamicreportsentity.platform.notification.Mailer;
import com.gucardev.dynamicreportsentity.platform.notification.OutgoingMail;
import com.gucardev.dynamicreportsentity.platform.notification.TemplateRenderer;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Opens a single-use link to the survey for this step, mails it and waits. Running it again (a retry) reuses the
 * same link. {@link SurveyFlowBridge} resumes the step with the answers.
 */
@Component
class SurveyStepHandler implements StepHandler {

    static final String SOURCE_TYPE = "flow-step";
    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.of("tr"))
            .withZone(ZoneId.of("Europe/Istanbul"));

    private final SurveyService surveys;
    private final Mailer mailer;
    private final TemplateRenderer renderer;
    private final Clock clock;
    private final String uiBaseUrl;

    SurveyStepHandler(SurveyService surveys, Mailer mailer, TemplateRenderer renderer, Clock clock,
                      @Value("${approvals.ui-base-url:http://localhost:5173}") String uiBaseUrl) {
        this.surveys = surveys;
        this.mailer = mailer;
        this.renderer = renderer;
        this.clock = clock;
        this.uiBaseUrl = uiBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public String type() {
        return SurveyStep.TYPE;
    }

    @Override
    public StepResult execute(StepContext context) {
        ClaimedStep claimed = context.step();
        SurveyStep step = (SurveyStep) claimed.step();
        Map<String, Object> data = RunData.of(claimed, clock);
        String title = surveys.title(step.surveyId()).orElseThrow(() ->
                new StepException("SURVEY_NOT_FOUND", "Survey " + step.surveyId() + " was deleted.", false));
        Instant until = clock.instant().plus(Duration.ofHours(step.timeoutHoursOrDefault()));
        String token;
        try {
            token = surveys.invite(step.surveyId(), SOURCE_TYPE, Long.toString(claimed.stepRunId()), until);
        } catch (ApiException e) {
            throw new StepException(e.code(), e.getMessage(), false);
        }
        String link = uiBaseUrl + "/s/" + step.surveyId() + "?invitation=" + token;
        List<String> to = step.to().stream().map(address -> renderer.renderText(address, data).strip())
                .filter(address -> !address.isBlank()).toList();
        List<String> refused = to.stream().filter(address -> !mailer.allows(address)).toList();
        if (to.isEmpty() || !refused.isEmpty()) {
            throw new StepException("RECIPIENT_NOT_ALLOWED", to.isEmpty() ? "No recipient."
                    : "Not allowed recipients: " + refused, false);
        }
        String subject = step.subject() == null || step.subject().isBlank() ? "Anket: " + title
                : renderer.renderText(step.subject(), data);
        String message = step.message() == null ? "" : renderer.renderText(step.message(), data);
        mailer.send(new OutgoingMail(to, List.of(), List.of(), subject,
                CssInliner.inline(html(title, message, link, until, claimed.flowName())), List.of()));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("surveyId", step.surveyId());
        output.put("surveyTitle", title);
        output.put("link", link);
        output.put("sentTo", to);
        output.put("dueAt", until.toString());
        return StepResult.waitUntil(output, until);
    }

    private static String html(String title, String message, String link, Instant until, String flowName) {
        return """
                <style>
                  body { margin: 0; padding: 24px 0; background: #f4f4f5; }
                  .wrap { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 10px;
                    border-top: 4px solid #dc2626; padding: 28px; font-family: -apple-system, Segoe UI, Roboto,
                    Helvetica, Arial, sans-serif; font-size: 14px; line-height: 1.55; color: #18181b; }
                  .eyebrow { margin: 0 0 6px; color: #dc2626; font-size: 12px; font-weight: 600; letter-spacing: .04em;
                    text-transform: uppercase; }
                  h1 { margin: 0 0 16px; font-size: 20px; line-height: 1.3; }
                  .message { white-space: pre-line; margin: 0 0 20px; }
                  a.button { display: inline-block; background: #dc2626; color: #ffffff; text-decoration: none;
                    font-weight: 600; padding: 11px 22px; border-radius: 8px; }
                  .note { color: #71717a; font-size: 12px; margin: 20px 0 0; }
                  .footer { margin-top: 20px; padding-top: 12px; border-top: 1px solid #e4e4e7; color: #a1a1aa;
                    font-size: 12px; }
                </style>
                <div class="wrap">
                  <p class="eyebrow">Yanıtınız bekleniyor</p>
                  <h1>%s</h1>
                  <p class="message">%s</p>
                  <a class="button" href="%s">Anketi doldur</a>
                  <p class="note">Bağlantı tek kullanımlıktır ve %s tarihine kadar geçerlidir.</p>
                  <p class="footer">Bu e-posta “%s” akışı tarafından gönderildi.</p>
                </div>
                """.formatted(escape(title), escape(message), escape(link), DUE.format(until), escape(flowName));
    }

    private static String escape(String text) {
        return text == null ? "" : HtmlUtils.htmlEscape(text);
    }
}
