package com.gucardev.dynamicreportsentity.platform.assistant;

import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns "every Monday at 9", "her gece 3'te" into a 5-field cron expression. {@code check_cron} answers the next
 * fire times, so the model can see whether the expression means what the user said; schedules may not fire more
 * often than every 5 minutes, which is checked too. The draft is {@code {cron, description}}.
 */
@Component
class CronAssistant implements Drafter {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter FIRES = DateTimeFormatter.ofPattern("EEEE yyyy-MM-dd HH:mm", Locale.ENGLISH);
    private static final List<Map<String, Object>> TOOLS = tools();
    private static final String PROMPT = """
            You turn a schedule described in plain language (often Turkish) into a cron expression for a job
            scheduler. Now it is {{NOW}} in Europe/Istanbul; expressions run in that time zone.
            - Use the usual FIVE fields: minute hour day-of-month month day-of-week. Day names MON..SUN or 0-7,
              ranges (MON-FRI), lists (1,15), steps (*/15). No seconds field, no year, no '?' or 'L'.
            - The job may run at most every 5 minutes.
            - Times without minutes mean minute 0 ("saat 9" = 0 9 * * *). "gece 3" = 03:00, "akşam 6" = 18:00,
              "öğlen" = 12:00, "sabah" without a time = 09:00, "hafta içi" = MON-FRI, "hafta sonu" = SAT,SUN,
              "ayın ilk günü" = day 1.
            Always call `check_cron` and compare the next fire times with the request, then call `submit_cron` with a
            short description in the user's language (e.g. "Her pazartesi 09:00"). If the request is not a
            schedule or is ambiguous, ask ONE short question in plain text.
            """;

    private final ChatClient chat;
    private final AssistantProperties properties;
    private final Clock clock;

    CronAssistant(ChatClient chat, AssistantProperties properties, Clock clock) {
        this.chat = chat;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String kind() {
        return "cron";
    }

    @Override
    public SuggestionResult draft(List<ChatMessage> conversation, Object current, Actor viewer) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(ToolLoop.message("system", PROMPT.replace("{{NOW}}",
                ZonedDateTime.now(clock).withZoneSameInstant(ZONE).format(FIRES))));
        if (current != null) {
            messages.add(ToolLoop.message("system", "Current expression: " + ToolLoop.JSON.writeValueAsString(current)));
        }
        conversation.forEach(turn -> messages.add(ToolLoop.message(turn.role(), turn.content())));
        return ToolLoop.run(chat, properties, messages, TOOLS, (name, arguments, said, iteration, model) -> {
            String cron = ToolLoop.text(arguments, "cron");
            Map<String, Object> check = check(cron);
            return switch (name) {
                case "check_cron" -> new ToolLoop.ToolAnswer(check);
                case "submit_cron" -> {
                    if (!Boolean.TRUE.equals(check.get("valid"))) {
                        yield new ToolLoop.ToolAnswer(check);
                    }
                    Map<String, Object> draft = new LinkedHashMap<>();
                    draft.put("cron", cron);
                    draft.put("description", ToolLoop.text(arguments, "description"));
                    yield new ToolLoop.Done(new SuggestionResult(said, ToolLoop.text(arguments, "description"),
                            ToolLoop.text(arguments, "description"), null, List.of(), draft, iteration, model));
                }
                default -> new ToolLoop.ToolAnswer(Map.of("error", "Use check_cron or submit_cron."));
            };
        }, "Bunu bir zamanlamaya çeviremedim; örneğin “her pazartesi 09:00” gibi yazar mısınız?",
                "The assistant could not find a valid cron expression.");
    }

    /** Valid, at most every 5 minutes, and the next fire times for the model to compare. */
    Map<String, Object> check(String cron) {
        if (cron == null || cron.isBlank() || cron.strip().split("\\s+").length != 5) {
            return Map.of("valid", false, "error", "Write exactly five fields: minute hour day month weekday.");
        }
        CronExpression expression;
        try {
            expression = CronExpression.parse("0 " + cron.strip());
        } catch (IllegalArgumentException e) {
            return Map.of("valid", false, "error", e.getMessage());
        }
        List<String> next = new ArrayList<>();
        LocalDateTime at = LocalDateTime.now(clock.withZone(ZONE));
        LocalDateTime previous = null;
        for (int i = 0; i < 5; i++) {
            at = expression.next(at);
            if (at == null) {
                break;
            }
            if (previous != null && previous.plusMinutes(5).isAfter(at)) {
                return Map.of("valid", false, "error", "Runs more often than every 5 minutes.");
            }
            next.add(at.format(FIRES));
            previous = at;
        }
        if (next.isEmpty()) {
            return Map.of("valid", false, "error", "Never fires.");
        }
        return Map.of("valid", true, "nextFireTimes", next);
    }

    private static List<Map<String, Object>> tools() {
        Map<String, Object> cron = Map.of("type", "string", "description", "Five-field cron expression");
        return List.of(
                ToolLoop.function("check_cron", "Checks an expression; returns {valid, nextFireTimes} or "
                        + "{valid: false, error}.", Map.of("type", "object", "properties", Map.of("cron", cron),
                        "required", List.of("cron"))),
                ToolLoop.function("submit_cron", "Hands in the final expression with a short description.",
                        Map.of("type", "object", "properties", Map.of("cron", cron, "description",
                                Map.of("type", "string")), "required", List.of("cron", "description"))));
    }
}
