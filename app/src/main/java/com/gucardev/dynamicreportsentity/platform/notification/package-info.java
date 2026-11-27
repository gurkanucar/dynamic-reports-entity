/**
 * Notification module: Mustache mail templates (shared by every business unit), rendering them safely and sending
 * e-mails within the company's recipient policy. Knows nothing about flows or approvals: whoever uses a template
 * tells it through {@link com.gucardev.dynamicreportsentity.platform.notification.MailTemplateUsage}.
 */
@ApplicationModule(id = "notification", displayName = "Notifications", allowedDependencies = {"api", "identity"})
package com.gucardev.dynamicreportsentity.platform.notification;

import org.springframework.modulith.ApplicationModule;
