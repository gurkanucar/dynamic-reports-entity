package com.gucardev.dynamicreportsentity.platform.flow.core;

import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Cron handling: users write the usual 5 fields; Spring's {@link CronExpression} wants seconds too. */
final class CronSchedules {

    private CronSchedules() {
    }

    /** @throws IllegalArgumentException if the expression is invalid */
    static CronExpression parse(String cron) {
        String expression = cron.strip();
        if (!expression.startsWith("@") && expression.split("\\s+").length == 5) {
            expression = "0 " + expression;
        }
        return CronExpression.parse(expression);
    }

    /** First fire time strictly after {@code after}; null if there is none. */
    static Instant next(CronExpression cron, ZoneId zone, Instant after) {
        ZonedDateTime next = cron.next(after.atZone(zone));
        return next == null ? null : next.toInstant();
    }

    static Instant next(String cron, String zone, Instant after) {
        return next(parse(cron), ZoneId.of(zone), after);
    }
}
