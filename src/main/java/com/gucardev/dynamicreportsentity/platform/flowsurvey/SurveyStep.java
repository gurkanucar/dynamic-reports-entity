package com.gucardev.dynamicreportsentity.platform.flowsurvey;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowStep;
import com.gucardev.dynamicreportsentity.platform.flow.spi.Lane;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

/**
 * Sends a survey and waits for its answer: e-mails a single-use link to {@code to} and goes on once someone answers
 * through it (outcome {@code answered}, output {@code surveyId, responseId, respondent, answers, text}) or after
 * {@code timeoutHours} ({@code timeout}). E.g. "ask the new hire's manager for the equipment list, then order it".
 *
 * @param to      addresses (Mustache, e.g. {@code {{trigger.managerEmail}}})
 * @param subject mail subject (Mustache); default "Anket: <survey title>"
 * @param message a few lines above the button (Mustache)
 */
public record SurveyStep(
        @NotBlank @Pattern(regexp = FlowStep.KEY_PATTERN, message = FlowStep.KEY_MESSAGE) String key,
        @NotNull UUID surveyId,
        @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 254) String> to,
        @Size(max = 300) String subject,
        @Size(max = 4000) String message,
        @Min(1) @Max(720) Integer timeoutHours,
        @Min(1) @Max(60) Integer timeoutSeconds,
        @Min(1) @Max(10) Integer maxAttempts) implements FlowStep {

    public static final String TYPE = "survey";
    public static final int DEFAULT_TIMEOUT_HOURS = 72;

    @Override
    @JsonIgnore
    public String typeName() {
        return TYPE;
    }

    @Override
    @JsonIgnore
    public Lane lane() {
        return Lane.LIGHT;
    }

    @Override
    @JsonIgnore
    public boolean continuesInSequence(String outcome) {
        return "answered".equals(outcome);
    }

    @Override
    @JsonIgnore
    public Set<String> outcomes() {
        return Set.of("answered", "timeout");
    }

    @JsonIgnore
    public int timeoutHoursOrDefault() {
        return timeoutHours == null ? DEFAULT_TIMEOUT_HOURS : timeoutHours;
    }
}
