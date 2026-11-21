/**
 * Report steps for flows: plugs the reporting module into the flow engine through its SPI. Neither of the two
 * knows the other; this adapter depends on both.
 */
@ApplicationModule(id = "flow-report", displayName = "Flow report steps",
        allowedDependencies = {"api", "flow :: spi", "reporting", "reporting :: query", "identity"})
package com.gucardev.dynamicreportsentity.platform.flowreport;

import org.springframework.modulith.ApplicationModule;
