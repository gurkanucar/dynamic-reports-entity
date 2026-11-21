package com.gucardev.dynamicreportsentity.platform.flowsurvey;

import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowEvents;
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

    SurveyFlowBridge(FlowEvents flows) {
        this.flows = flows;
    }

    @ApplicationModuleListener
    void on(SurveyResponseSubmitted submitted) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("surveyId", submitted.surveyId());
        payload.put("surveyTitle", submitted.surveyTitle());
        payload.put("responseId", submitted.responseId());
        payload.put("respondent", submitted.respondent());
        payload.put("submittedAt", submitted.submittedAt().toString());
        payload.put("answers", submitted.answers());
        payload.put("text", submitted.display());
        payload.put("labels", submitted.labels());
        flows.publish(SOURCE_PREFIX + submitted.surveyId(), "response:" + submitted.responseId(), payload);
    }
}
