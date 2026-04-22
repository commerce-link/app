package pl.commercelink.starter.email;

import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.ClientNotificationsConfiguration;

public interface NotificationConfigProvider {
    ClientNotificationsConfiguration getConfig(String storeId);
    boolean supports(String storeId, EmailNotificationType type);
}
