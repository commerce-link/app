package pl.commercelink.starter.email;

import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.ClientNotificationsConfiguration;

/**
 * What sending one customer email needs from the store, read once per email.
 *
 * @param senderName   display name in the From header, or null to send from the bare address
 * @param replyToEmail address customer replies go to, or null when the store has none
 */
public record NotificationSettings(ClientNotificationsConfiguration configuration, String senderName, String replyToEmail) {

    public boolean supports(EmailNotificationType type) {
        return configuration != null && configuration.supports(type);
    }
}
