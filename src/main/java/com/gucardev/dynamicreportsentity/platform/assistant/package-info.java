/**
 * Report assistant: turns a business user's request in plain language into a report definition, by talking to any
 * OpenAI-compatible chat API (OpenRouter, a company-hosted vLLM/Ollama, ...).
 * <ul>
 *   <li><b>What leaves the company:</b> the user's messages and the catalog <i>metadata</i> they may use (entity,
 *       field and relation names, labels and types). Never data rows: previews run in the browser against
 *       {@code /api/reports/run}, after the model is done.</li>
 *   <li><b>No made-up reports:</b> the model must validate its definition with the same report builder that runs
 *       reports, as the requesting user, and fix it until it passes.</li>
 *   <li><b>Optional:</b> off unless {@code assistant.enabled} and a model and key are configured.</li>
 *   <li><b>Asynchronous and multi-instance:</b> a request answers 202 at once; the result is stored in the
 *       database, so any instance can answer the polling.</li>
 * </ul>
 */
@ApplicationModule(id = "assistant", displayName = "Report assistant",
        allowedDependencies = {"api", "reporting", "reporting :: query", "reporting :: catalog"})
package com.gucardev.dynamicreportsentity.platform.assistant;

import org.springframework.modulith.ApplicationModule;
