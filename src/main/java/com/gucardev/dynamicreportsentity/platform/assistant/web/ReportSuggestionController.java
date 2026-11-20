package com.gucardev.dynamicreportsentity.platform.assistant.web;

import com.gucardev.dynamicreportsentity.platform.api.ApiResponse;
import com.gucardev.dynamicreportsentity.platform.assistant.ChatMessage;
import com.gucardev.dynamicreportsentity.platform.assistant.ReportSuggestion;
import com.gucardev.dynamicreportsentity.platform.assistant.SuggestionService;
import com.gucardev.dynamicreportsentity.platform.assistant.SuggestionService.SuggestionRequest;
import com.gucardev.dynamicreportsentity.platform.reporting.ReportViewer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * "Describe the report you need": the assistant builds a validated report definition. Asynchronous: POST answers
 * 202 with the suggestion's URL; GET it until {@code status} is {@code completed} or {@code failed}.
 * Contract: {@code api/assistant-api.yaml}.
 */
@RestController
@RequestMapping("/api/report-suggestions")
@PreAuthorize("hasAuthority('REPORT_RUN')")
class ReportSuggestionController {

    record MessageRequest(@NotNull @Pattern(regexp = "user|assistant") String role,
                          @NotBlank @Size(max = 4000) String content) {
    }

    /** @param currentDefinition the definition on screen, also drafts that are not valid yet; null = new report */
    record SuggestionBody(@NotEmpty @Size(max = 30) List<@NotNull @Valid MessageRequest> messages,
                          Map<String, Object> currentDefinition) {
    }

    private final SuggestionService suggestions;

    ReportSuggestionController(SuggestionService suggestions) {
        this.suggestions = suggestions;
    }

    /** Whether the assistant is available, so the UI can hide it. */
    @GetMapping("/settings")
    ApiResponse<Map<String, Object>> settings() {
        return ApiResponse.of(suggestions.settings());
    }

    @PostMapping
    ResponseEntity<ApiResponse<SuggestionView>> request(@Valid @RequestBody SuggestionBody body,
                                                        Authentication authentication) {
        SuggestionRequest request = new SuggestionRequest(body.messages().stream()
                .map(message -> new ChatMessage(message.role(), message.content())).toList(),
                body.currentDefinition());
        ReportSuggestion suggestion = suggestions.request(request, viewer(authentication));
        return ResponseEntity.accepted()
                .location(URI.create("/api/report-suggestions/" + suggestion.getId()))
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(ApiResponse.of(SuggestionView.of(suggestion, null)));
    }

    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<SuggestionView>> get(@PathVariable long id, Authentication authentication) {
        ReportSuggestion suggestion = suggestions.get(id, viewer(authentication));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (suggestion.isActive()) {
            response.header(HttpHeaders.RETRY_AFTER, "1");
        }
        return response.body(ApiResponse.of(SuggestionView.of(suggestion, suggestions.result(suggestion))));
    }

    static ReportViewer viewer(Authentication authentication) {
        return new ReportViewer(authentication.getName(), authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).filter(Objects::nonNull).collect(Collectors.toSet()));
    }
}
