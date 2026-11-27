package com.gucardev.dynamicreportsentity.platform.flow.web;

import com.gucardev.dynamicreportsentity.platform.api.ApiResponse;
import com.gucardev.dynamicreportsentity.platform.flow.core.TriggerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where other systems send events (no login: the unguessable URL and the HMAC signature authenticate the call).
 * Answers fast: the run is only queued. 202 when a run was started, 200 when the event was filtered out or was a
 * redelivery of an event already handled (senders must not retry those).
 */
@RestController
class FlowHookController {

    private final TriggerService triggers;

    FlowHookController(TriggerService triggers) {
        this.triggers = triggers;
    }

    @PostMapping("/api/flow-hooks/{token}")
    ResponseEntity<ApiResponse<TriggerService.Delivery>> receive(@PathVariable String token,
                                                                 @RequestBody(required = false) byte[] body,
                                                                 @RequestHeader(value = "X-Signature", required = false) String signature,
                                                                 @RequestHeader(value = "X-Event-Id", required = false) String eventId) {
        TriggerService.Delivery delivery = triggers.receiveWebhook(token, body == null ? new byte[0] : body, signature,
                eventId);
        return ResponseEntity.status(delivery.runId() != null ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(ApiResponse.of(delivery));
    }
}
