package pl.commercelink.web.notifications;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import pl.commercelink.notifications.StoreNotificationRecord;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;
import pl.commercelink.web.settings.StoreSettingsCatalog;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class NotificationViewFactory {

    public List<NotificationView> toViews(List<StoreNotificationRecord> records, UserRole role) {
        return records.stream().map(record -> toView(record, role)).toList();
    }

    public NotificationView toView(StoreNotificationRecord record, UserRole role) {
        StoreNotificationType type = record.getType();
        String titleKey = type == null ? "store.notification.type.default" : "store.notification.type." + type.name();
        String openHref = NotificationPaths.base(role, record.getStoreId()) + "/"
                + UriUtils.encodePathSegment(record.getNotificationId(), StandardCharsets.UTF_8) + "/open";
        String actionHref = null;
        String actionKey = null;
        if (type == StoreNotificationType.UNAUTHENTICATED) {
            actionHref = StoreSettingsCatalog.homeHref(role, record.getStoreId()) + "/marketplaces";
            actionKey = "store.notification.action.reconnect";
        } else if (type == StoreNotificationType.MARKETPLACE_RETURN_REFUNDED && role == UserRole.ADMIN
                && StringUtils.isNotBlank(record.getObject())) {
            // the RMA screen resolves the store from the logged-in admin, so only the store admin gets the link
            actionHref = "/dashboard/rma/" + UriUtils.encodePathSegment(record.getObject(), StandardCharsets.UTF_8);
            actionKey = "store.notification.action.viewReturn";
        }
        return new NotificationView(record.getNotificationId(), titleKey, record.getMessage(), record.getCreatedAt(),
                record.isUnread(), record.getSeverity() == StoreNotificationSeverity.WARNING, actionHref, actionKey,
                openHref);
    }
}
