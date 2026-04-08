package pl.commercelink.starter.email;

import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.ClientNotificationsConfig;

public interface NotificationConfigProvider {
    ClientNotificationsConfig getConfig(String storeId);
    boolean supports(String storeId, EmailNotificationType type);
}
