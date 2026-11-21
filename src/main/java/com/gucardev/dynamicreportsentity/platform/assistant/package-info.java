/**
 * Assistant: turns a business user's request in plain language into a draft (a dataset definition, a styled mail
 * template or a whole flow), by talking to any OpenAI-compatible chat API (OpenRouter, a company-hosted vLLM/Ollama,
 * ...). Drafts are never saved here: the user reviews and fine-tunes them in the editors.
 * <ul>
 *   <li><b>What leaves the company:</b> the user's messages and <i>metadata</i>: the catalog they may use (entity,
 *       field and relation names, labels and types), names of saved datasets, mail templates and approval groups. Never data rows: previews run in the browser against
 *       {@code /api/datasets/run}, after the model is done.</li>
 *   <li><b>Nothing made up:</b> the model must validate its draft with the same checks as saving it (dataset
 *       builder, template syntax, flow rules), as the requesting user, and fix it until it passes.</li>
 *   <li><b>Optional:</b> off unless {@code assistant.enabled} and a model and key are configured.</li>
 *   <li><b>Asynchronous and multi-instance:</b> a request answers 202 at once; the result is stored in the
 *       database, so any instance can answer the polling.</li>
 * </ul>
 */
@ApplicationModule(id = "assistant", displayName = "Assistant",
        allowedDependencies = {"api", "identity", "dataset", "dataset :: query", "dataset :: catalog",
                "dataset :: snapshot", "notification", "approval", "flow :: draft"})
package com.gucardev.dynamicreportsentity.platform.assistant;

import org.springframework.modulith.ApplicationModule;
