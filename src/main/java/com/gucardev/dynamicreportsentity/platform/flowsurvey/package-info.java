/**
 * Survey responses as flow events: every accepted response is published to the flow engine under the source
 * {@code survey:<id>}, so a flow with an {@code event} trigger on that source runs with the response as
 * {@code trigger} (e.g. open an approval for a request form, mail the answers, call a webhook). Neither module knows
 * the other.
 */
@ApplicationModule(id = "flow-survey", displayName = "Flow survey triggers",
        allowedDependencies = {"flow :: spi", "survey"})
package com.gucardev.dynamicreportsentity.platform.flowsurvey;

import org.springframework.modulith.ApplicationModule;
