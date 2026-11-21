/**
 * Dataset steps for flows: plugs the datasets module into the flow engine through its SPI. Neither of the two
 * knows the other; this adapter depends on both.
 */
@ApplicationModule(id = "flow-dataset", displayName = "Flow dataset steps",
        allowedDependencies = {"api", "flow :: spi", "dataset", "dataset :: query", "dataset :: saved", "identity"})
package com.gucardev.dynamicreportsentity.platform.flowdataset;

import org.springframework.modulith.ApplicationModule;
