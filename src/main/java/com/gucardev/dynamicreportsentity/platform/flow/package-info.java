/**
 * Flows module: business users chain steps into a graph and start it by hand, on a schedule, by webhook or Kafka.
 * The engine knows no step type by name: types plug in through the {@code spi} package (the engine's own mail,
 * condition, filter, webhook and Kafka steps too; reports and approvals come from adapter modules).
 * <p>
 * The engine needs nothing but the database: runs and steps are rows, every app instance polls for ready
 * steps and claims them under a lease, exactly like dataset refreshes in the reporting module. See
 * {@link com.gucardev.dynamicreportsentity.platform.flow.core.FlowEngine}.
 */
@ApplicationModule(id = "flow", displayName = "Flows", allowedDependencies = {"api", "identity", "notification", "storage"})
package com.gucardev.dynamicreportsentity.platform.flow;

import org.springframework.modulith.ApplicationModule;
