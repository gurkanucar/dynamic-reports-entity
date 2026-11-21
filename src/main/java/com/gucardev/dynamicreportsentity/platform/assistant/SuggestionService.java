package com.gucardev.dynamicreportsentity.platform.assistant;

import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Asynchronous suggestions. {@link #request} stores the request and answers at once; a virtual thread on the same
 * instance talks to the model (at most {@code assistant.max-concurrent} at a time, the rest wait) and stores the
 * outcome. Polling works on any instance. If an instance dies mid-request, the request times out after
 * {@code assistant.timeout} instead of hanging forever.
 */
@Slf4j
@Service
@EnableConfigurationProperties(AssistantProperties.class)
public class SuggestionService {

    /**
     * What the client sends: the chat so far (last turn from the user) and what is on screen (a dataset definition,
     * a template, a flow), if anything.
     */
    public record SuggestionRequest(List<ChatMessage> messages, Object current) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final DatasetSuggestionRepository suggestions;
    private final Map<String, Drafter> drafters;
    private final AssistantProperties properties;
    private final Clock clock;
    private final Semaphore slots;
    private final ExecutorService workers = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("assistant-", 1).factory());

    SuggestionService(DatasetSuggestionRepository suggestions, List<Drafter> drafters,
                      AssistantProperties properties, Clock clock) {
        this.suggestions = suggestions;
        this.drafters = drafters.stream().collect(Collectors.toMap(Drafter::kind, Function.identity()));
        this.properties = properties;
        this.clock = clock;
        this.slots = new Semaphore(Math.max(1, properties.maxConcurrent()));
    }

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();   // unfinished requests time out
    }

    public Map<String, Object> settings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("enabled", properties.isUsable());
        settings.put("model", properties.isUsable() ? properties.model() : null);
        settings.put("provider", properties.isUsable() ? properties.baseUrl().getHost() : null);
        return settings;
    }

    /** @param kind {@code dataset}, {@code mail-template} or {@code flow} */
    public DatasetSuggestion request(String kind, SuggestionRequest request, Actor viewer) {
        if (!properties.isUsable()) {
            throw new AssistantException(503, "ASSISTANT_DISABLED",
                    "The assistant is not configured (assistant.enabled, assistant.model, assistant.api-key).");
        }
        Drafter drafter = drafters.get(kind);
        if (drafter == null) {
            throw new IllegalArgumentException("Unknown assistant kind " + kind);
        }
        List<ChatMessage> conversation = request.messages();
        if (!"user".equals(conversation.getLast().role())) {
            throw new AssistantException(422, "LAST_MESSAGE_NOT_FROM_USER",
                    "The last message must be the user's request.");
        }
        DatasetSuggestion suggestion = suggestions.save(new DatasetSuggestion(kind, viewer.username(),
                JSON.writeValueAsString(request), clock.instant()));
        long id = suggestion.getId();
        try {
            workers.execute(() -> work(id, drafter, request, viewer));
        } catch (RejectedExecutionException e) {
            suggestions.fail(id, "ASSISTANT_UNAVAILABLE", "The instance is shutting down; try again.", clock.instant());
        }
        return suggestion;
    }

    private void work(long id, Drafter drafter, SuggestionRequest request, Actor viewer) {
        try {
            slots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            if (suggestions.start(id, clock.instant()) == 0) {
                return;   // timed out while waiting
            }
            SuggestionResult result = drafter.draft(request.messages(), request.current(), viewer);
            suggestions.complete(id, JSON.writeValueAsString(result), result.model(), clock.instant());
        } catch (AssistantException e) {
            log.info("Suggestion {} failed: {} {}", id, e.code(), e.getMessage());
            suggestions.fail(id, e.code(), truncate(e.getMessage()), clock.instant());
        } catch (RuntimeException e) {
            log.error("Suggestion {} failed", id, e);
            suggestions.fail(id, "INTERNAL_ERROR", "Unexpected error (details in the server log)", clock.instant());
        } finally {
            slots.release();
        }
    }

    /**
     * The owner's suggestion of this kind; others' answer 404. A request older than the timeout is reported as timed
     * out.
     */
    public DatasetSuggestion get(String kind, long id, Actor viewer) {
        DatasetSuggestion suggestion = suggestions.findById(id)
                .filter(found -> found.getOwner().equals(viewer.username()) && found.getKind().equals(kind))
                .orElseThrow(() -> new AssistantException(404, "SUGGESTION_NOT_FOUND",
                        "Suggestion " + id + " does not exist."));
        if (suggestion.isActive() && suggestion.getCreatedAt().plus(properties.timeout()).isBefore(clock.instant())) {
            suggestions.fail(id, "SUGGESTION_TIMED_OUT", "The assistant did not answer in time.", clock.instant());
            return suggestions.findById(id).orElseThrow();
        }
        return suggestion;
    }

    public SuggestionResult result(DatasetSuggestion suggestion) {
        return suggestion.getResultJson() == null ? null
                : JSON.readValue(suggestion.getResultJson(), SuggestionResult.class);
    }

    /** Every instance runs this; both statements are idempotent. */
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT10M")
    void cleanUp() {
        Instant now = clock.instant();
        suggestions.expire(now.minus(properties.timeout()), now);
        suggestions.deleteOlderThan(now.minus(properties.retention()));
    }

    private static String truncate(String text) {
        return text == null || text.length() <= 1000 ? text : text.substring(0, 999) + "…";
    }
}
