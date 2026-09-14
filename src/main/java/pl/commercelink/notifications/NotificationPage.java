package pl.commercelink.notifications;

import pl.commercelink.stores.StoreNotificationType;

import java.util.List;

public record NotificationPage(List<StoreNotificationRecord> items, int page, int totalPages, int totalItems,
                               long unreadCount, List<StoreNotificationType> types) {
}
