package com.gucardev.dynamicreportsentity.platform.survey;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

/**
 * The questions of a survey and its rules, as stored (JSON) and edited.
 *
 * @param fields questions and section headings, in order
 * @param rules  checks over all answers; a response that breaks one is refused with its message
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SurveyDefinition(String submitLabel, String successMessage, List<Field> fields, List<Rule> rules) {

    /** Every question type; {@code section} is a heading without an answer. */
    public static final List<String> TYPES = List.of("shortText", "longText", "email", "phone", "number",
            "singleChoice", "multipleChoice", "dropdown", "date", "rating", "nps", "yesNo", "section");

    public static final Set<String> CHOICE_TYPES = Set.of("singleChoice", "multipleChoice", "dropdown");

    static final JsonMapper JSON = JsonMapper.builder().build();

    public SurveyDefinition {
        fields = fields == null ? List.of() : List.copyOf(fields);
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /**
     * One question.
     *
     * @param id          the answer's key (also in rules: {@code {"var": "email"}}), e.g. {@code email}
     * @param min         number: smallest value; multipleChoice: fewest choices
     * @param max         number: largest value; rating: stars (3-10, default 5); multipleChoice: most choices
     * @param visibleWhen JSON Logic over the earlier answers; the question is asked only when it is true
     * @param width       {@code half}: two half questions share a row; absent = full
     * @param showInResults counted in the results people may see ({@link SurveySettings#results()}); choice, yes/no,
     *                      rating and score questions only, never personal answers
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Field(String id, String type, String label, String help, String placeholder, Boolean required,
                        List<Option> options, Double min, Double max, Integer minLength, Integer maxLength,
                        Object visibleWhen, String width, Boolean showInResults) {

        public boolean isRequired() {
            return Boolean.TRUE.equals(required) && answerable() && !"yesNo".equals(type);
        }

        public boolean answerable() {
            return !"section".equals(type);
        }

        public boolean choice() {
            return CHOICE_TYPES.contains(type);
        }

        public int stars() {
            return max == null ? 5 : max.intValue();
        }
    }

    public record Option(String id, String label) {
    }

    /** A check over all answers, e.g. "the end date is after the start date". */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Rule(String id, String message, Object logic) {
    }

    public static SurveyDefinition fromJson(String json) {
        return JSON.readValue(json, SurveyDefinition.class);
    }

    public String toJson() {
        return JSON.writeValueAsString(this);
    }

    /** Converts a loosely typed value (a map from a request or the assistant) into a definition. */
    public static SurveyDefinition from(Object value) {
        return JSON.convertValue(value, SurveyDefinition.class);
    }

    public List<Field> answerableFields() {
        return fields.stream().filter(Field::answerable).toList();
    }

    /** Without the rules' logic: what a respondent's browser gets. */
    SurveyDefinition forRespondent() {
        return new SurveyDefinition(submitLabel, successMessage, fields,
                rules.stream().map(rule -> new Rule(rule.id(), rule.message(), null)).toList());
    }
}
