package com.gucardev.dynamicreportsentity.platform.survey;

import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.api.JsonLogic;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyDefinition.Field;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyDefinition.Option;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyDefinition.Rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks a survey definition before it is saved (and the assistant's drafts): known types, unique ids, options for
 * choice questions, sensible limits, and conditions and rules that are valid JSON Logic naming existing questions.
 * A condition may only look at questions <i>before</i> its own, so the form never depends on what comes later.
 */
final class SurveyChecker {

    static final int MAX_FIELDS = 100;
    static final int MAX_OPTIONS = 100;
    static final int MAX_RULES = 50;
    static final int MAX_TEXT_LENGTH = 10_000;
    private static final Pattern ID = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{0,39}");
    private static final Pattern OPTION_ID = Pattern.compile("[a-zA-Z0-9_-]{1,40}");

    private SurveyChecker() {
    }

    static List<FieldProblem> check(SurveyDefinition definition) {
        List<FieldProblem> problems = new ArrayList<>();
        if (definition == null) {
            problems.add(new FieldProblem("definition", "REQUIRED", "The survey needs its questions."));
            return problems;
        }
        length(problems, "definition.submitLabel", definition.submitLabel(), 60);
        length(problems, "definition.successMessage", definition.successMessage(), 1000);
        List<Field> fields = definition.fields();
        if (fields.stream().noneMatch(field -> field != null && field.answerable())) {
            problems.add(new FieldProblem("definition.fields", "REQUIRED", "The survey needs at least one question."));
        }
        if (fields.size() > MAX_FIELDS) {
            problems.add(new FieldProblem("definition.fields", "TOO_MANY",
                    "A survey may have at most " + MAX_FIELDS + " questions and headings."));
        }
        Set<String> earlier = new LinkedHashSet<>();
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            String path = "definition.fields[" + i + "]";
            if (field == null) {
                problems.add(new FieldProblem(path, "REQUIRED", "Empty question."));
                continue;
            }
            checkField(problems, path, field, earlier);
            if (field.id() != null && !earlier.add(field.id())) {
                problems.add(new FieldProblem(path + ".id", "DUPLICATE_ID", "Two questions have the id '"
                        + field.id() + "'."));
            }
        }
        Set<String> answerable = new HashSet<>();
        fields.stream().filter(field -> field != null && field.answerable() && field.id() != null)
                .forEach(field -> answerable.add(field.id()));
        List<Rule> rules = definition.rules();
        if (rules.size() > MAX_RULES) {
            problems.add(new FieldProblem("definition.rules", "TOO_MANY", "At most " + MAX_RULES + " rules."));
        }
        Set<String> ruleIds = new HashSet<>();
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            String path = "definition.rules[" + i + "]";
            if (rule == null) {
                problems.add(new FieldProblem(path, "REQUIRED", "Empty rule."));
                continue;
            }
            if (rule.id() == null || !ID.matcher(rule.id()).matches()) {
                problems.add(new FieldProblem(path + ".id", "INVALID_FORMAT",
                        "A rule id starts with a letter: letters, digits and _ (at most 40)."));
            } else if (!ruleIds.add(rule.id())) {
                problems.add(new FieldProblem(path + ".id", "DUPLICATE_ID", "Two rules have the id '" + rule.id() + "'."));
            }
            if (rule.message() == null || rule.message().isBlank()) {
                problems.add(new FieldProblem(path + ".message", "REQUIRED",
                        "A rule needs the message people see when they break it."));
            }
            length(problems, path + ".message", rule.message(), 300);
            if (rule.logic() == null) {
                problems.add(new FieldProblem(path + ".logic", "REQUIRED", "A rule needs its condition."));
            } else {
                logic(problems, path + ".logic", rule.logic(), answerable, "a question of the survey");
            }
        }
        return problems;
    }

    private static void checkField(List<FieldProblem> problems, String path, Field field, Set<String> earlier) {
        if (field.id() == null || !ID.matcher(field.id()).matches()) {
            problems.add(new FieldProblem(path + ".id", "INVALID_FORMAT",
                    "A question id starts with a letter: letters, digits and _ (at most 40), e.g. email."));
        }
        if (field.type() == null || !SurveyDefinition.TYPES.contains(field.type())) {
            problems.add(new FieldProblem(path + ".type", "UNKNOWN_TYPE", "Unknown question type '" + field.type()
                    + "'; one of " + SurveyDefinition.TYPES + "."));
            return;
        }
        if (field.label() == null || field.label().isBlank()) {
            problems.add(new FieldProblem(path + ".label", "REQUIRED", "A question needs its text."));
        }
        length(problems, path + ".label", field.label(), 500);
        length(problems, path + ".help", field.help(), 1000);
        length(problems, path + ".placeholder", field.placeholder(), 200);
        if (field.width() != null && !field.width().equals("full") && !field.width().equals("half")) {
            problems.add(new FieldProblem(path + ".width", "INVALID_VALUE", "width is full or half."));
        }
        if (field.choice()) {
            options(problems, path, field.options());
        } else if (field.options() != null && !field.options().isEmpty()) {
            problems.add(new FieldProblem(path + ".options", "NOT_ALLOWED", "Only choice questions have options."));
        }
        switch (field.type()) {
            case "rating" -> {
                if (field.max() != null && (field.max() < 3 || field.max() > 10 || field.max() % 1 != 0)) {
                    problems.add(new FieldProblem(path + ".max", "OUT_OF_RANGE", "A rating has 3 to 10 stars."));
                }
            }
            case "number", "multipleChoice" -> {
                if (field.min() != null && field.max() != null && field.min() > field.max()) {
                    problems.add(new FieldProblem(path + ".min", "OUT_OF_RANGE", "min is larger than max."));
                }
            }
            case "shortText", "longText", "email", "phone" -> {
                if (field.minLength() != null && field.minLength() < 0
                        || field.maxLength() != null && (field.maxLength() < 1 || field.maxLength() > MAX_TEXT_LENGTH)) {
                    problems.add(new FieldProblem(path + ".maxLength", "OUT_OF_RANGE",
                            "Lengths are between 0 and " + MAX_TEXT_LENGTH + "."));
                } else if (field.minLength() != null && field.maxLength() != null
                        && field.minLength() > field.maxLength()) {
                    problems.add(new FieldProblem(path + ".minLength", "OUT_OF_RANGE",
                            "minLength is larger than maxLength."));
                }
            }
            default -> {
                // nothing more to check
            }
        }
        if (field.visibleWhen() != null) {
            logic(problems, path + ".visibleWhen", field.visibleWhen(), earlier, "an earlier question");
        }
    }

    private static void options(List<FieldProblem> problems, String path, List<Option> options) {
        if (options == null || options.size() < 2) {
            problems.add(new FieldProblem(path + ".options", "REQUIRED", "A choice question needs at least 2 options."));
            return;
        }
        if (options.size() > MAX_OPTIONS) {
            problems.add(new FieldProblem(path + ".options", "TOO_MANY", "At most " + MAX_OPTIONS + " options."));
        }
        Set<String> ids = new HashSet<>();
        for (int j = 0; j < options.size(); j++) {
            Option option = options.get(j);
            String optionPath = path + ".options[" + j + "]";
            if (option == null || option.id() == null || !OPTION_ID.matcher(option.id()).matches()) {
                problems.add(new FieldProblem(optionPath + ".id", "INVALID_FORMAT",
                        "An option id is 1-40 letters, digits, _ or -."));
                continue;
            }
            if (!ids.add(option.id())) {
                problems.add(new FieldProblem(optionPath + ".id", "DUPLICATE_ID", "Two options have the id '"
                        + option.id() + "'."));
            }
            if (option.label() == null || option.label().isBlank()) {
                problems.add(new FieldProblem(optionPath + ".label", "REQUIRED", "An option needs its text."));
            }
            length(problems, optionPath + ".label", option.label(), 200);
        }
    }

    /** Valid JSON Logic whose variables are among {@code allowed}. */
    private static void logic(List<FieldProblem> problems, String path, Object rule, Set<String> allowed,
                              String what) {
        try {
            JsonLogic.check(rule);
        } catch (JsonLogic.InvalidRuleException e) {
            problems.add(new FieldProblem(path, "INVALID_EXPRESSION", e.getMessage()));
            return;
        }
        for (String variable : variables(rule)) {
            String root = variable.split("\\.")[0];
            if (!allowed.contains(root)) {
                problems.add(new FieldProblem(path, "UNKNOWN_FIELD_REFERENCE", "'" + variable
                        + "' is not " + what + "; use the id of " + what + "."));
            }
        }
    }

    /** The paths a rule reads with {@code var}. */
    static Set<String> variables(Object rule) {
        Set<String> found = new LinkedHashSet<>();
        collect(rule, found);
        return found;
    }

    private static void collect(Object rule, Set<String> found) {
        if (rule instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if ("var".equals(entry.getKey())) {
                    Object argument = entry.getValue() instanceof List<?> list && !list.isEmpty() ? list.getFirst()
                            : entry.getValue();
                    if (argument instanceof String path && !path.isBlank()) {
                        found.add(path);
                    }
                } else {
                    collect(entry.getValue(), found);
                }
            }
        } else if (rule instanceof List<?> list) {
            list.forEach(item -> collect(item, found));
        }
    }

    private static void length(List<FieldProblem> problems, String path, String value, int max) {
        if (value != null && value.length() > max) {
            problems.add(new FieldProblem(path, "INVALID_LENGTH", "At most " + max + " characters."));
        }
    }
}
