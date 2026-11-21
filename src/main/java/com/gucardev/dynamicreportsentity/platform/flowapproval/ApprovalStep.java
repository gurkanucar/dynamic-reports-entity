package com.gucardev.dynamicreportsentity.platform.flowapproval;

import java.util.UUID;
import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowStep;
import com.gucardev.dynamicreportsentity.platform.flow.spi.Lane;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Waits for a member of an approval group to approve or reject. The run holds no thread and no lease while it waits
 * (days if need be); outcome {@code approved}, {@code rejected} or, after {@code timeoutHours}, {@code timeout}.
 * Without edges, anything but {@code approved} ends the run.
 *
 * @param title             shown to approvers (Mustache over {@code {flow, run, trigger, steps}})
 * @param description       optional details (Mustache)
 * @param allowSelfApproval whether the person who started the run, or the flow owner, may approve it (default no)
 * @param notifyMembers     e-mail the group members that have an address (default yes)
 */
public record ApprovalStep(
        @NotBlank @Pattern(regexp = FlowStep.KEY_PATTERN, message = FlowStep.KEY_MESSAGE) String key,
        @NotNull UUID groupId,
        @NotBlank @Size(max = 300) String title,
        @Size(max = 4000) String description,
        @Min(1) @Max(720) Integer timeoutHours,
        Boolean allowSelfApproval,
        Boolean notifyMembers,
        @Min(1) @Max(60) Integer timeoutSeconds,
        @Min(1) @Max(10) Integer maxAttempts) implements FlowStep {

    public static final String TYPE = "approval";
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
        return "approved".equals(outcome);
    }

    @Override
    @JsonIgnore
    public Set<String> outcomes() {
        return Set.of("approved", "rejected", "timeout");
    }

    @JsonIgnore
    public int timeoutHoursOrDefault() {
        return timeoutHours == null ? DEFAULT_TIMEOUT_HOURS : timeoutHours;
    }
}
