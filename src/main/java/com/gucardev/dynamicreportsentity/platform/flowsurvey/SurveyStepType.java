package com.gucardev.dynamicreportsentity.platform.flowsurvey;

import com.gucardev.dynamicreportsentity.platform.flow.spi.StepCheck;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepReference;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepType;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyService;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Checks survey steps: the survey exists, the texts are valid templates. */
@Component
class SurveyStepType implements StepType<SurveyStep> {

    static final String SURVEY = "survey";

    private final SurveyService surveys;

    SurveyStepType(SurveyService surveys) {
        this.surveys = surveys;
    }

    @Override
    public String name() {
        return SurveyStep.TYPE;
    }

    @Override
    public Class<SurveyStep> stepClass() {
        return SurveyStep.class;
    }

    @Override
    public void validate(SurveyStep step, StepCheck check) {
        if (!surveys.exists(step.surveyId())) {
            check.problem("surveyId", "SURVEY_NOT_FOUND", "Survey " + step.surveyId() + " does not exist.");
        }
        for (int i = 0; i < step.to().size(); i++) {
            check.template("to[" + i + "]", step.to().get(i));
        }
        if (step.subject() != null) {
            check.template("subject", step.subject());
        }
        if (step.message() != null) {
            check.template("message", step.message());
        }
    }

    @Override
    public Set<StepReference> references(SurveyStep step) {
        return step.surveyId() == null ? Set.of() : Set.of(StepReference.of(SURVEY, step.surveyId()));
    }
}
