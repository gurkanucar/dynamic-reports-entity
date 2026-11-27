package com.gucardev.dynamicreportsentity.platform.assistant.web;

import java.util.UUID;
import com.gucardev.dynamicreportsentity.platform.api.ApiResponse;
import com.gucardev.dynamicreportsentity.platform.assistant.ChatMessage;
import com.gucardev.dynamicreportsentity.platform.assistant.DatasetSuggestion;
import com.gucardev.dynamicreportsentity.platform.assistant.SuggestionService;
import com.gucardev.dynamicreportsentity.platform.assistant.SuggestionService.SuggestionRequest;
import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The asynchronous request/poll pair every assistant has: POST answers 202 with the suggestion's URL; GET it until
 * {@code status} is {@code completed} or {@code failed}. Subclasses only name the path and the kind.
 */
abstract class SuggestionEndpoints {

    record MessageRequest(@NotNull @Pattern(regexp = "user|assistant") String role,
                          @NotBlank @Size(max = 4000) String content) {
    }

    /**
     * @param current           what is on screen (a template, a flow), also drafts that are not valid yet
     * @param currentDefinition the dataset on screen (dataset assistant); null = start from scratch
     */
    record SuggestionBody(@NotEmpty @Size(max = 30) List<@NotNull @Valid MessageRequest> messages,
                          Map<String, Object> current, Map<String, Object> currentDefinition) {
    }

    private final SuggestionService suggestions;
    private final String kind;
    private final String path;

    SuggestionEndpoints(SuggestionService suggestions, String kind, String path) {
        this.suggestions = suggestions;
        this.kind = kind;
        this.path = path;
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
                body.current() != null ? body.current() : body.currentDefinition());
        DatasetSuggestion suggestion = suggestions.request(kind, request, viewer(authentication));
        return ResponseEntity.accepted()
                .location(URI.create(path + "/" + suggestion.getId()))
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(ApiResponse.of(SuggestionView.of(suggestion, null)));
    }

    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<SuggestionView>> get(@PathVariable UUID id, Authentication authentication) {
        DatasetSuggestion suggestion = suggestions.get(kind, id, viewer(authentication));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (suggestion.isActive()) {
            response.header(HttpHeaders.RETRY_AFTER, "1");
        }
        return response.body(ApiResponse.of(SuggestionView.of(suggestion, suggestions.result(suggestion))));
    }

    static Actor viewer(Authentication authentication) {
        return new Actor(authentication.getName(), authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).filter(Objects::nonNull).collect(Collectors.toSet()));
    }
}
