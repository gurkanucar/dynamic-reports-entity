package com.gucardev.dynamicreportsentity.platform.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

/**
 * {@code assistant.*}.
 *
 * @param enabled        master switch; also needs {@code model} and {@code apiKey}
 * @param baseUrl        OpenAI-compatible API root ({@code POST {baseUrl}/chat/completions})
 * @param apiKey         sent as {@code Authorization: Bearer}; keep it in the environment, never in the repo
 * @param model          model id at the provider, e.g. {@code openai/gpt-4.1-mini} on OpenRouter; it must support
 *                       tool calling
 * @param appName        sent as {@code X-Title} (OpenRouter shows it in its usage pages)
 * @param requestTimeout one call to the model; drafting a whole flow can take a reasoning model a couple of minutes
 * @param timeout        a whole suggestion (several calls); afterwards it is reported as timed out
 * @param maxIterations  model calls per suggestion (each failed validation costs one)
 * @param maxConcurrent  suggestions worked on at the same time per instance; more wait (never rejected)
 * @param retention      suggestions are deleted after this
 * @param reasoning      for models that think before answering (OpenRouter {@code reasoning}): off, low, medium,
 *                       high; empty = the model's default. "off" makes a reasoning model answer much faster
 */
@ConfigurationProperties("assistant")
public record AssistantProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://openrouter.ai/api/v1") URI baseUrl,
        String apiKey,
        String model,
        @DefaultValue("dynamic-datasets") String appName,
        @DefaultValue("150s") Duration requestTimeout,
        @DefaultValue("6m") Duration timeout,
        @DefaultValue("6") int maxIterations,
        @DefaultValue("4") int maxConcurrent,
        @DefaultValue("1d") Duration retention,
        @DefaultValue("0.0") double temperature,
        @DefaultValue("") String reasoning) {

    public boolean isUsable() {
        return enabled && apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
    }
}
