package pl.commercelink.web.notifications;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import pl.commercelink.notifications.StoreNotificationRecord;
import pl.commercelink.receipts.ReceiptAttemptKeys;
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
        } else if (type == StoreNotificationType.RECEIPT_ATTENTION && role == UserRole.ADMIN
                && StringUtils.isNotBlank(record.getObject())) {
            // the order screen resolves the store from the logged-in admin, so only the store admin gets the link
            actionHref = "/dashboard/orders/" + UriUtils.encodePathSegment(
                    ReceiptAttemptKeys.orderPartOf(record.getObject()), StandardCharsets.UTF_8);
            actionKey = "store.notification.action.viewOrder";
        } else if (type == StoreNotificationType.DELIVERY_REQUEST_REJECTED && role == UserRole.ADMIN) {
            // the pending deliveries screen resolves the store from the logged-in admin, so only the store admin gets the link
            actionHref = "/dashboard/deliveries/preview";
            actionKey = "store.notification.action.viewPendingDeliveries";
        } else if ((type == StoreNotificationType.WAREHOUSE_SHIPMENT_CREATED
                || type == StoreNotificationType.WAREHOUSE_SHIPMENT_PICKUP) && role == UserRole.ADMIN
                && StringUtils.contains(record.getObject(), ':')) {
            // the label is fetched with the logged-in admin's store integration; an error there returns to the bell
            String provider = StringUtils.substringBefore(record.getObject(), ":");
            String externalId = StringUtils.substringAfter(record.getObject(), ":");
            actionHref = "/dashboard/shipping/labels/" + UriUtils.encodePathSegment(provider, StandardCharsets.UTF_8)
                    + "/" + UriUtils.encodePathSegment(externalId, StandardCharsets.UTF_8)
                    + "?back=/dashboard/notifications";
            actionKey = "store.notification.action.downloadLabel";
        } else if ((type == StoreNotificationType.RMA_RETURN_SHIPMENT_FAILED
                || type == StoreNotificationType.RMA_RETURN_PICKUP_FAILED) && role == UserRole.ADMIN
                && StringUtils.isNotBlank(record.getObject())) {
            // the object is rmaId:attempt, so each failed attempt is its own notification
            actionHref = "/dashboard/rma/" + UriUtils.encodePathSegment(
                    StringUtils.substringBefore(record.getObject(), ":"), StandardCharsets.UTF_8);
            actionKey = "store.notification.action.viewReturn";
        }
        return new NotificationView(record.getNotificationId(), titleKey, record.getMessage(), record.getCreatedAt(),
                record.isUnread(), record.getSeverity() == StoreNotificationSeverity.WARNING, actionHref, actionKey,
                openHref);
    }
}
