package com.gucardev.dynamicreportsentity.platform.notification.web;

import com.gucardev.dynamicreportsentity.platform.api.ApiResponse;

import com.gucardev.dynamicreportsentity.platform.identity.Actor;
import com.gucardev.dynamicreportsentity.platform.notification.MailTemplate;
import com.gucardev.dynamicreportsentity.platform.notification.MailTemplateService;
import com.gucardev.dynamicreportsentity.platform.notification.web.NotificationApi.PreviewRequest;
import com.gucardev.dynamicreportsentity.platform.notification.web.NotificationApi.TemplateDetail;
import com.gucardev.dynamicreportsentity.platform.notification.web.NotificationApi.TemplateRequest;
import com.gucardev.dynamicreportsentity.platform.notification.web.NotificationApi.TemplateSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.etag;
import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.expectedVersion;
import static com.gucardev.dynamicreportsentity.platform.api.HttpSupport.paging;

/** Mail templates (Mustache) and rendering a draft with sample data. */
@RestController
@PreAuthorize("hasAuthority('PLATFORM_USER')")
class MailTemplateController {

    private final MailTemplateService templates;

    MailTemplateController(MailTemplateService templates) {
        this.templates = templates;
    }

    @GetMapping("/api/mail-templates")
    ApiResponse<List<TemplateSummary>> list(@RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        int[] paging = paging(page, size);
        return ApiResponse.page(templates.list(paging[0], paging[1]), NotificationApi::summary);
    }

    @PostMapping("/api/mail-templates")
    ResponseEntity<ApiResponse<TemplateDetail>> create(@Valid @RequestBody TemplateRequest request,
                                                       Authentication authentication) {
        MailTemplate template = templates.create(request.input(), Actor.of(authentication));
        return ResponseEntity.created(URI.create("/api/mail-templates/" + template.getId()))
                .eTag(etag(template.getVersion()))
                .body(ApiResponse.of(NotificationApi.detail(template)));
    }

    @GetMapping("/api/mail-templates/{id}")
    ResponseEntity<ApiResponse<TemplateDetail>> get(@PathVariable long id) {
        MailTemplate template = templates.get(id);
        return ResponseEntity.ok().eTag(etag(template.getVersion())).body(ApiResponse.of(NotificationApi.detail(template)));
    }

    @PutMapping("/api/mail-templates/{id}")
    ResponseEntity<ApiResponse<TemplateDetail>> update(@PathVariable long id,
                                                       @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                       @Valid @RequestBody TemplateRequest request,
                                                       Authentication authentication) {
        long expected = expectedVersion(ifMatch);
        MailTemplate template = templates.update(id, expected, request.input(), Actor.of(authentication));
        return ResponseEntity.ok().eTag(etag(template.getVersion())).body(ApiResponse.of(NotificationApi.detail(template)));
    }

    @DeleteMapping("/api/mail-templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id, Authentication authentication) {
        templates.delete(id, Actor.of(authentication));
    }

    /** Renders a draft; nothing is saved. Without {@code model}, a sample run is used. */
    @PostMapping("/api/mail-template-previews")
    ApiResponse<Map<String, String>> preview(@Valid @RequestBody PreviewRequest request) {
        return ApiResponse.of(templates.preview(request.subject(), request.bodyHtml(), request.model()));
    }

    /** The data a template sees in a sample run, to show template authors what they can use. */
    @GetMapping("/api/mail-template-previews/sample-model")
    ApiResponse<Map<String, Object>> sampleModel() {
        return ApiResponse.of(templates.sampleModel());
    }
}
