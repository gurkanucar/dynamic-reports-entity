/**
 * Survey responses as flow events: every accepted response is published to the flow engine under the source
 * {@code survey:<id>}, so a flow with an {@code event} trigger on that source runs with the response as
 * {@code trigger} (e.g. open an approval for a request form, mail the answers, call a webhook). Neither module knows
 * the other.
 * <p>
 * The {@code survey} step goes the other way: it e-mails a single-use link to a survey and waits until it is
 * answered (or times out); the answers are the step's output ({@code steps.<key>.answers}, {@code .text}).
 */
@ApplicationModule(id = "flow-survey", displayName = "Flow survey triggers",
        allowedDependencies = {"api", "flow :: spi", "survey", "notification"})
package com.gucardev.dynamicreportsentity.platform.flowsurvey;

import org.springframework.modulith.ApplicationModule;
