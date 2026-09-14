package pl.commercelink.notifications;

import java.util.List;

public record NotificationPage(List<StoreNotificationRecord> items, int page, int totalPages, int totalItems,
                               long unreadCount) {
}
