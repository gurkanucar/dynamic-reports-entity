package com.gucardev.dynamicreportsentity.platform.flow.spi;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What expressions (JSON Logic) and templates (Mustache) of a step see:
 * {@code flow {id, name}}, {@code run {id, trigger, triggeredBy, startedAt, date}}, {@code trigger} (the webhook body or
 * Kafka message that started the run; null otherwise) and {@code steps.<key>} (outputs of the steps that ran before).
 */
public final class RunData {

    private RunData() {
    }

    public static Map<String, Object> of(ClaimedStep claimed, Clock clock) {
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("id", claimed.runId());
        run.put("trigger", claimed.trigger().name().toLowerCase());
        run.put("triggeredBy", claimed.triggeredBy());
        run.put("startedAt", claimed.runStartedAt() == null ? "" : claimed.runStartedAt().toString());
        run.put("date", LocalDate.ofInstant(clock.instant(), ZoneId.systemDefault()).toString());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flow", Map.of("id", claimed.flowId(), "name", claimed.flowName()));
        data.put("run", run);
        data.put("trigger", claimed.triggerPayload());
        data.put("steps", claimed.previousOutputs());
        return data;
    }
}
