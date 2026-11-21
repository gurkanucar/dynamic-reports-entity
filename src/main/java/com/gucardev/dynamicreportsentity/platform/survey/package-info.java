/**
 * Surveys: business users design a questionnaire (13 question types, sections, half-width layout), show questions
 * only for some answers ({@code visibleWhen}) and refuse answers that break their rules ({@code rules}); both are
 * JSON Logic, evaluated on the server. Platform users answer published surveys; the owner reads the responses, their
 * summary and an Excel export.
 * <p>
 * Every accepted response publishes {@link com.gucardev.dynamicreportsentity.platform.survey.SurveyResponseSubmitted},
 * which the {@code flow-survey} adapter turns into flow runs, so a response can open an approval, send a mail, call a
 * webhook. The module knows nothing about flows.
 */
@ApplicationModule(id = "survey", displayName = "Surveys", allowedDependencies = {"api", "identity"})
package com.gucardev.dynamicreportsentity.platform.survey;

import org.springframework.modulith.ApplicationModule;
