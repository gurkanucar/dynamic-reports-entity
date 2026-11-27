package com.gucardev.dynamicreportsentity.platform.approval;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings of {@code approvals.*}.
 *
 * @param uiBaseUrl            where the UI runs, for the link in notification e-mails
 * @param timeoutCheckInterval how often each instance looks for requests past their deadline
 */
@ConfigurationProperties("approvals")
public record ApprovalProperties(@DefaultValue("http://localhost:5173") String uiBaseUrl,
                                 @DefaultValue("PT30S") Duration timeoutCheckInterval) {
}
