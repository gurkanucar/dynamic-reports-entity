package com.gucardev.dynamicreportsentity.platform.flowsurvey;

import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowEvents;
import com.gucardev.dynamicreportsentity.platform.flow.spi.WaitingStepEnded;
import com.gucardev.dynamicreportsentity.platform.flow.spi.WaitingSteps;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyService;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyResponseSubmitted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Starts the flows listening on a survey when a response arrives, after the response committed and at least once;
 * the response id is the event key, so a redelivery starts no second run.
 * <p>
 * The run's {@code trigger}: {@code surveyId, surveyTitle, responseId, respondent, submittedAt, answers} (by question
 * id: option ids, numbers, booleans, texts), {@code text} (the same answers as people read them) and {@code labels}
 * (question texts).
 */
@Component
class SurveyFlowBridge {

    static final String SOURCE_PREFIX = "survey:";

    private final FlowEvents flows;
    private final WaitingSteps steps;
    private final SurveyService surveys;

    SurveyFlowBridge(FlowEvents flows, WaitingSteps steps, SurveyService surveys) {
        this.flows = flows;
        this.steps = steps;
        this.surveys = surveys;
    }

    /** A survey step's link was answered: the step goes on with the answers. */
    @ApplicationModuleListener
    void resume(SurveyResponseSubmitted submitted) {
        if (!SurveyStepHandler.SOURCE_TYPE.equals(submitted.sourceType())) {
            return;
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("responseId", submitted.responseId());
        output.put("respondent", submitted.respondent());
        output.put("submittedAt", submitted.submittedAt().toString());
        output.put("answers", submitted.answers());
        output.put("text", submitted.display());
        output.put("labels", submitted.labels());
        steps.resume(Long.parseLong(submitted.sourceId()), "answered", output, "survey_answered",
                (submitted.respondent() == null ? "Anonim" : submitted.respondent()) + " yanıtladı");
    }

    /** The step stopped waiting (timeout, run cancelled): its link stops working. */
    @ApplicationModuleListener
    void on(WaitingStepEnded ended) {
        if (SurveyStep.TYPE.equals(ended.stepType())) {
            surveys.revoke(SurveyStepHandler.SOURCE_TYPE, Long.toString(ended.stepRunId()));
        }
    }

    @ApplicationModuleListener
    void on(SurveyResponseSubmitted submitted) {
        if (submitted.sourceType() != null) {
            return;   // answered for a waiting step, not a new event
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("surveyId", submitted.surveyId());
        payload.put("surveyTitle", submitted.surveyTitle());
        payload.put("responseId", submitted.responseId());
        payload.put("respondent", submitted.respondent());
        // who stands behind the run (e.g. may not approve their own request); null for anonymous surveys
        payload.put("startedBy", submitted.respondent());
        payload.put("submittedAt", submitted.submittedAt().toString());
        payload.put("answers", submitted.answers());
        payload.put("text", submitted.display());
        payload.put("labels", submitted.labels());
        flows.publish(SOURCE_PREFIX + submitted.surveyId(), "response:" + submitted.responseId(), payload);
    }
}
