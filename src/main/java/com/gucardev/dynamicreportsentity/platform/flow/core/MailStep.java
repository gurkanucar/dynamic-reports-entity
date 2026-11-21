package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowStep;
import com.gucardev.dynamicreportsentity.platform.flow.spi.Lane;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Sends an e-mail rendered from a mail template. The template sees {@code flow}, {@code run} and the outputs
 * of the earlier steps as {@code steps.<key>}; {@code attachments} lists dataset steps whose CSV file is attached.
 * <p>
 * Sent at most once per step run: a retry after a successful send does not send again. If the instance dies
 * between handing the mail to the server and recording it, the retry sends it a second time (at least once).
 */
public record MailStep(
        @NotBlank @Pattern(regexp = FlowStep.KEY_PATTERN, message = FlowStep.KEY_MESSAGE) String key,
        @NotNull Long templateId,
        @NotEmpty @Size(max = 50) List<@NotBlank @Email String> to,
        @Size(max = 50) List<@NotBlank @Email String> cc,
        @Size(max = 10) List<@NotBlank String> attachments,
        @Min(1) @Max(600) Integer timeoutSeconds,
        @Min(1) @Max(10) Integer maxAttempts) implements FlowStep {

    public static final String TYPE = "mail";

    public MailStep {
        to = to == null ? null : List.copyOf(to);
        cc = cc == null ? List.of() : List.copyOf(cc);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

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
}
