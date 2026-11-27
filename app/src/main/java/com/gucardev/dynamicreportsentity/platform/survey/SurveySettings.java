package com.gucardev.dynamicreportsentity.platform.survey;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * How the owner works with the responses, beside the questions.
 *
 * @param tracking which follow-up controls a response has (status, priority, follow-up date, assignee) and extra
 *                 fields the owner fills in per response (e.g. "Sipariş no"); never shown to respondents
 * @param results  who sees the counted answers of questions marked {@code showInResults}: {@code owner} (default),
 *                 {@code respondents} (after answering) or {@code everyone} who can open the survey
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SurveySettings(Tracking tracking, String results) {

    public static final List<String> RESULTS = List.of("owner", "respondents", "everyone");
    public static final List<String> TRACKING_TYPES = List.of("text", "choice", "date", "number");

    public static final SurveySettings DEFAULT = new SurveySettings(Tracking.DEFAULT, "owner");

    public SurveySettings {
        tracking = tracking == null ? Tracking.DEFAULT : tracking;
        results = results == null || results.isBlank() ? "owner" : results;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tracking(boolean status, boolean priority, boolean followUpDate, boolean assignee,
                           List<TrackingField> fields) {

        static final Tracking DEFAULT = new Tracking(true, true, true, true, List.of());

        public Tracking {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    /** An extra field the owner fills in on each response, e.g. "Sipariş no" (text) or "Kategori" (choice). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TrackingField(String id, String label, String type, List<SurveyDefinition.Option> options) {
    }

    public static SurveySettings fromJson(String json) {
        return json == null || json.isBlank() ? DEFAULT : SurveyDefinition.JSON.readValue(json, SurveySettings.class);
    }

    public static SurveySettings from(Object value) {
        return value == null ? DEFAULT : SurveyDefinition.JSON.convertValue(value, SurveySettings.class);
    }

    String toJson() {
        return SurveyDefinition.JSON.writeValueAsString(this);
    }
}
