/**
 * Flows module: business users chain steps (run a report, send it by e-mail, ...) and start them by hand
 * or on a schedule. Phase 1 of the flow engine: sequential steps, cron schedules, retries, cancellation.
 * <p>
 * The engine needs nothing but the database: runs and steps are rows, every app instance polls for ready
 * steps and claims them under a lease, exactly like dataset refreshes in the reporting module. See
 * {@link com.gucardev.dynamicreportsentity.platform.flow.core.FlowEngine}.
 */
@ApplicationModule(id = "flow", displayName = "Flows", allowedDependencies = {"reporting", "reporting :: query", "storage"})
package com.gucardev.dynamicreportsentity.platform.flow;

import org.springframework.modulith.ApplicationModule;
