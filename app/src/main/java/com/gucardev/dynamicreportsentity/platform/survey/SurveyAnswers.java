package com.gucardev.dynamicreportsentity.platform.survey;

import com.gucardev.dynamicreportsentity.platform.api.ApiException.FieldProblem;
import com.gucardev.dynamicreportsentity.platform.api.JsonLogic;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyDefinition.Field;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyDefinition.Option;
import com.gucardev.dynamicreportsentity.platform.survey.SurveyDefinition.Rule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks a response the way the form does, but on the server, where it cannot be skipped: questions hidden by their
 * condition are dropped, required ones must be answered, every answer must fit its type and limits, and then the
 * survey's rules run over all answers.
 * <p>
 * Stored answers: text as text, a choice as its option id (a list of ids for multiple choice), a date as
 * {@code YYYY-MM-DD}, numbers, ratings and scores as numbers, yes/no as a boolean. Ids rather than labels, so
 * renaming an option keeps old answers.
 */
final class SurveyAnswers {

    /** The checked answers, or what is wrong with them (field {@code answers.<id>} or {@code rules.<id>}). */
    record Outcome(Map<String, Object> answers, List<FieldProblem> problems) {
        boolean valid() {
            return problems.isEmpty();
        }
    }

    private static final int DEFAULT_SHORT_TEXT = 500;
    private static final int DEFAULT_LONG_TEXT = 5000;
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}");
    private static final Pattern PHONE = Pattern.compile("[+0-9 ()./-]{5,30}");

    private SurveyAnswers() {
    }

    static Outcome check(SurveyDefinition definition, Map<String, Object> given) {
        Map<String, Object> raw = given == null ? Map.of() : given;
        Map<String, Object> answers = new LinkedHashMap<>();
        List<FieldProblem> problems = new ArrayList<>();
        for (Field field : definition.fields()) {
            if (!field.answerable() || !visible(field, answers)) {
                continue;
            }
            String path = "answers." + field.id();
            Object value = raw.get(field.id());
            if (empty(value)) {
                if (field.isRequired()) {
                    problems.add(new FieldProblem(path, "REQUIRED", "Bu soru zorunlu."));
                }
                continue;
            }
            try {
                answers.put(field.id(), normalize(field, value));
            } catch (InvalidAnswer e) {
                problems.add(new FieldProblem(path, e.code, e.getMessage()));
            }
        }
        if (problems.isEmpty()) {
            for (Rule rule : definition.rules()) {
                boolean passed;
                try {
                    passed = JsonLogic.test(rule.logic(), answers);
                } catch (RuntimeException e) {
                    passed = false;
                }
                if (!passed) {
                    problems.add(new FieldProblem("rules." + rule.id(), "RULE_FAILED", rule.message()));
                }
            }
        }
        return new Outcome(answers, problems);
    }

    /** Whether the question is asked, given the answers to the questions before it. */
    static boolean visible(Field field, Map<String, Object> answers) {
        if (field.visibleWhen() == null) {
            return true;
        }
        try {
            return JsonLogic.test(field.visibleWhen(), answers);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean empty(Object value) {
        return value == null || value instanceof String text && text.isBlank()
                || value instanceof Collection<?> collection && collection.isEmpty();
    }

    private static Object normalize(Field field, Object value) {
        return switch (field.type()) {
            case "shortText", "longText" -> text(field, value,
                    field.type().equals("shortText") ? DEFAULT_SHORT_TEXT : DEFAULT_LONG_TEXT);
            case "email" -> {
                String text = text(field, value, 254);
                if (!EMAIL.matcher(text).matches()) {
                    throw new InvalidAnswer("INVALID_EMAIL", "Geçerli bir e-posta adresi yazın.");
                }
                yield text;
            }
            case "phone" -> {
                String text = text(field, value, 30);
                if (!PHONE.matcher(text).matches()) {
                    throw new InvalidAnswer("INVALID_FORMAT", "Geçerli bir telefon numarası yazın.");
                }
                yield text;
            }
            case "number" -> {
                BigDecimal number = number(value);
                if (field.min() != null && number.compareTo(BigDecimal.valueOf(field.min())) < 0
                        || field.max() != null && number.compareTo(BigDecimal.valueOf(field.max())) > 0) {
                    throw new InvalidAnswer("OUT_OF_RANGE", "Değer " + range(field.min(), field.max()) + " olmalı.");
                }
                yield plain(number);
            }
            case "singleChoice", "dropdown" -> {
                String id = value instanceof String text ? text : null;
                if (id == null || option(field, id) == null) {
                    throw new InvalidAnswer("UNKNOWN_OPTION", "Listedeki seçeneklerden birini seçin.");
                }
                yield id;
            }
            case "multipleChoice" -> {
                if (!(value instanceof Collection<?> collection)) {
                    throw new InvalidAnswer("INVALID_VALUE", "Seçeneklerin listesi bekleniyor.");
                }
                LinkedHashSet<String> ids = new LinkedHashSet<>();
                for (Object item : collection) {
                    if (!(item instanceof String id) || option(field, id) == null) {
                        throw new InvalidAnswer("UNKNOWN_OPTION", "Listedeki seçeneklerden seçin.");
                    }
                    ids.add(id);
                }
                if (field.min() != null && ids.size() < field.min() || field.max() != null && ids.size() > field.max()) {
                    throw new InvalidAnswer("OUT_OF_RANGE", range(field.min(), field.max()) + " seçenek seçin.");
                }
                // in the order of the options, so equal answers look equal
                yield field.options().stream().map(Option::id).filter(ids::contains).toList();
            }
            case "date" -> {
                try {
                    yield LocalDate.parse(String.valueOf(value)).toString();
                } catch (DateTimeParseException e) {
                    throw new InvalidAnswer("INVALID_DATE", "Tarih YYYY-AA-GG biçiminde olmalı.");
                }
            }
            case "rating" -> whole(value, 1, field.stars());
            case "nps" -> whole(value, 0, 10);
            case "yesNo" -> switch (value) {
                case Boolean bool -> bool;
                case String text when text.equalsIgnoreCase("true") || text.equalsIgnoreCase("yes") -> true;
                case String text when text.equalsIgnoreCase("false") || text.equalsIgnoreCase("no") -> false;
                default -> throw new InvalidAnswer("INVALID_VALUE", "Evet veya hayır bekleniyor.");
            };
            default -> throw new InvalidAnswer("UNKNOWN_TYPE", "Bilinmeyen soru tipi.");
        };
    }

    private static String text(Field field, Object value, int defaultMax) {
        if (!(value instanceof String) && !(value instanceof Number)) {
            throw new InvalidAnswer("INVALID_VALUE", "Metin bekleniyor.");
        }
        String text = String.valueOf(value).strip();
        int max = field.maxLength() == null ? defaultMax : field.maxLength();
        if (text.length() > max) {
            throw new InvalidAnswer("INVALID_LENGTH", "En fazla " + max + " karakter.");
        }
        if (field.minLength() != null && text.length() < field.minLength()) {
            throw new InvalidAnswer("INVALID_LENGTH", "En az " + field.minLength() + " karakter.");
        }
        return text;
    }

    private static BigDecimal number(Object value) {
        try {
            return switch (value) {
                case Number number -> new BigDecimal(number.toString());
                case String text -> new BigDecimal(text.strip().replace(',', '.'));
                default -> throw new NumberFormatException();
            };
        } catch (NumberFormatException e) {
            throw new InvalidAnswer("INVALID_NUMBER", "Bir sayı yazın.");
        }
    }

    private static long whole(Object value, int min, int max) {
        BigDecimal number = number(value);
        if (number.stripTrailingZeros().scale() > 0 || number.intValue() < min || number.intValue() > max) {
            throw new InvalidAnswer("OUT_OF_RANGE", min + " ile " + max + " arasında bir değer seçin.");
        }
        return number.longValue();
    }

    private static Object plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() <= 0 ? (Object) stripped.longValue() : value.doubleValue();
    }

    private static String range(Double min, Double max) {
        String low = min == null ? null : BigDecimal.valueOf(min).stripTrailingZeros().toPlainString();
        String high = max == null ? null : BigDecimal.valueOf(max).stripTrailingZeros().toPlainString();
        if (low != null && high != null) {
            return low + " ile " + high + " arasında";
        }
        return low != null ? "en az " + low : "en fazla " + high;
    }

    static Option option(Field field, String id) {
        return field.options() == null ? null
                : field.options().stream().filter(option -> option.id().equals(id)).findFirst().orElse(null);
    }

    /** Answers as people read them: option labels instead of ids, Evet/Hayır, lists joined. */
    static Map<String, String> display(SurveyDefinition definition, Map<String, Object> answers) {
        Map<String, String> display = new LinkedHashMap<>();
        for (Field field : definition.answerableFields()) {
            Object value = answers.get(field.id());
            if (value != null) {
                display.put(field.id(), display(field, value));
            }
        }
        return display;
    }

    static String display(Field field, Object value) {
        if (value instanceof Boolean bool) {
            return bool ? "Evet" : "Hayır";
        }
        if (field.choice()) {
            List<?> ids = value instanceof List<?> list ? list : List.of(value);
            return ids.stream().map(id -> {
                Option option = option(field, String.valueOf(id));
                return option == null ? String.valueOf(id) : option.label();
            }).collect(Collectors.joining(", "));
        }
        return Objects.toString(value);
    }

    private static final class InvalidAnswer extends RuntimeException {
        private final String code;

        InvalidAnswer(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
