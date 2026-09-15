package pl.commercelink.notifications;

import pl.commercelink.stores.StoreNotificationType;

public record NotificationFilter(boolean unreadOnly, StoreNotificationType type) {

    public boolean matches(StoreNotificationRecord record) {
        return (!unreadOnly || record.isUnread()) && (type == null || type == record.getType());
    }
}
