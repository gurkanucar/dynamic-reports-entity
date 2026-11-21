package com.gucardev.dynamicreportsentity.platform.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Locale;

/**
 * Settings of {@code notifications.*}.
 *
 * @param mail e-mail settings
 */
@ConfigurationProperties("notifications")
public record NotificationProperties(@DefaultValue Mail mail) {

    /**
     * @param from                    sender address of every e-mail the platform sends
     * @param allowedRecipientDomains if not empty, e-mails may only go to these domains (e.g. the company's)
     */
    public record Mail(@DefaultValue("reports@localhost") String from,
                       @DefaultValue List<String> allowedRecipientDomains) {

        public boolean allows(String address) {
            if (allowedRecipientDomains == null || allowedRecipientDomains.isEmpty()) {
                return true;
            }
            String domain = address.substring(address.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
            return allowedRecipientDomains.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(domain));
        }
    }
}
