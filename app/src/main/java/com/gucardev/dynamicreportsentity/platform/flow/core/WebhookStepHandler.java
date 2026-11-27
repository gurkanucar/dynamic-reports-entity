package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.spi.StepResult;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepException;
import com.gucardev.dynamicreportsentity.platform.flow.spi.RunData;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepHandler;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepContext;
import com.gucardev.dynamicreportsentity.platform.notification.TemplateRenderer;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Calls the step's URL. Redirects are not followed (they could lead to a host that is not allowed); the host is
 * checked again at run time, as the allow-list may have changed since the flow was saved.
 */
@Component
class WebhookStepHandler implements StepHandler {

    private final TemplateRenderer renderer;
    private final FlowProperties properties;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    WebhookStepHandler(TemplateRenderer renderer, FlowProperties properties, Clock clock) {
        this.renderer = renderer;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String type() {
        return WebhookStep.TYPE;
    }

    @Override
    public StepResult execute(StepContext context) throws IOException, InterruptedException {
        WebhookStep step = (WebhookStep) context.step().step();
        URI url = URI.create(step.url().strip());
        if (!properties.webhook().allows(url.getHost())) {
            throw new StepException("WEBHOOK_HOST_NOT_ALLOWED", "Calls to " + url.getHost() + " are not allowed", false);
        }
        Map<String, Object> data = RunData.of(context.step(), clock);
        String contentType = step.contentTypeOrDefault();
        String body = step.body() == null ? null : contentType.toLowerCase(Locale.ROOT).contains("json")
                ? renderer.renderJson(step.body(), data) : renderer.renderPlain(step.body(), data);
        Duration timeout = Duration.between(clock.instant(), context.deadline());
        HttpRequest.Builder request = HttpRequest.newBuilder(url)
                .timeout(timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(1) : timeout)
                .header("User-Agent", "dynamic-datasets-flows/1.0")
                .header("X-Flow-Run-Id", String.valueOf(context.step().runId()))
                .header("X-Flow-Step", step.key())
                .method(step.methodOrDefault(), body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", contentType);
        }
        step.headers().forEach(request::header);

        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new StepException("WEBHOOK_TIMEOUT", "No answer from " + url.getHost() + " in time", true);
        } catch (IOException e) {
            throw new StepException("WEBHOOK_UNREACHABLE", url.getHost() + " could not be reached: " + e.getMessage(),
                    true);
        }
        int status = response.statusCode();
        String answer = response.body() == null ? "" : response.body();
        if (status < 200 || status >= 300) {
            boolean retryable = status == 408 || status == 429 || status >= 500;
            throw new StepException("WEBHOOK_FAILED", url.getHost() + " answered " + status + ": "
                    + Texts.truncate(answer, 300), retryable);
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", status);
        output.put("body", parsed(Texts.truncate(answer, properties.webhook().maxResponseChars())));
        output.put("calledAt", Instant.now(clock).toString());
        return StepResult.done(output);
    }

    /** JSON answers become data later steps can use ({@code steps.<key>.body.id}); anything else stays text. */
    private static Object parsed(String body) {
        String trimmed = body.strip();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return FlowDefinition.valueFromJson(trimmed);
            } catch (JacksonException e) {
                return body;
            }
        }
        return body;
    }
}
