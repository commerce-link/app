package pl.commercelink.web.notifications;

import org.junit.jupiter.api.Test;
import pl.commercelink.notifications.StoreNotificationIds;
import pl.commercelink.notifications.StoreNotificationRecord;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationViewFactoryTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 14, 15, 32);

    private final NotificationViewFactory factory = new NotificationViewFactory();

    private static StoreNotificationRecord record(StoreNotificationSeverity severity, StoreNotificationType type,
                                                  String object, String message) {
        StoreNotificationRecord record = new StoreNotificationRecord();
        record.setStoreId("store-1");
        record.setNotificationId(StoreNotificationIds.of(type, object, message));
        record.setSeverity(severity);
        record.setType(type);
        record.setObject(object);
        record.setMessage(message);
        record.setCreatedAt(CREATED_AT);
        record.setUnreadStoreId("store-1");
        return record;
    }

    @Test
    void turnsAnExpiredMarketplaceConnectionIntoAWarningWithAReconnectLink() {
        // given
        StoreNotificationRecord record = record(StoreNotificationSeverity.WARNING, StoreNotificationType.UNAUTHENTICATED,
                "allegro_marketplace", "Your connection to Allegro marketplace has expired");

        // when
        NotificationView admin = factory.toView(record, UserRole.ADMIN);
        NotificationView superAdmin = factory.toView(record, UserRole.SUPER_ADMIN);

        // then
        assertThat(admin).isEqualTo(new NotificationView("UNAUTHENTICATED:allegro_marketplace",
                "store.notification.type.UNAUTHENTICATED", "Your connection to Allegro marketplace has expired",
                CREATED_AT, true, true, "/dashboard/store/marketplaces", "store.notification.action.reconnect",
                "/dashboard/notifications/UNAUTHENTICATED:allegro_marketplace/open"));
        assertThat(superAdmin.actionHref()).isEqualTo("/dashboard/store/store-1/marketplaces");
        assertThat(superAdmin.openHref())
                .isEqualTo("/dashboard/store/store-1/notifications/UNAUTHENTICATED:allegro_marketplace/open");
    }

    @Test
    void encodesTheRmaIdInTheReturnActionLink() {
        // given
        StoreNotificationRecord refunded = record(StoreNotificationSeverity.WARNING,
                StoreNotificationType.MARKETPLACE_RETURN_REFUNDED, "rma 7/a", "Allegro refunded the buyer");

        // when
        NotificationView admin = factory.toView(refunded, UserRole.ADMIN);

        // then
        assertThat(admin.actionHref()).isEqualTo("/dashboard/rma/rma%207%2Fa");
    }

    @Test
    void linksARefundedReturnToItsRmaForTheStoreAdminOnly() {
        // given
        StoreNotificationRecord refunded = record(StoreNotificationSeverity.WARNING,
                StoreNotificationType.MARKETPLACE_RETURN_REFUNDED, "rma-7", "Allegro refunded the buyer");
        StoreNotificationRecord refundedWithoutRma = record(StoreNotificationSeverity.WARNING,
                StoreNotificationType.MARKETPLACE_RETURN_REFUNDED, " ", "Allegro refunded the buyer");

        // when
        NotificationView admin = factory.toView(refunded, UserRole.ADMIN);
        NotificationView adminWithoutRma = factory.toView(refundedWithoutRma, UserRole.ADMIN);
        NotificationView superAdmin = factory.toView(refunded, UserRole.SUPER_ADMIN);

        // then
        assertThat(admin.actionHref()).isEqualTo("/dashboard/rma/rma-7");
        assertThat(admin.actionKey()).isEqualTo("store.notification.action.viewReturn");
        assertThat(adminWithoutRma.actionHref()).isNull();
        // the RMA screen is scoped to the store of the logged-in admin, so the super admin would land elsewhere
        assertThat(superAdmin.actionHref()).isNull();
        assertThat(superAdmin.actionKey()).isNull();
    }

    @Test
    void showsOtherNotificationsWithoutAnActionAndInfoSeverityAsInfo() {
        // given
        List<StoreNotificationRecord> records = List.of(
                record(StoreNotificationSeverity.WARNING, StoreNotificationType.MARKETPLACE_RETURN_UNMATCHED, "ret-1", "Unmatched"),
                record(StoreNotificationSeverity.INFO, StoreNotificationType.WELCOME, null, "Welcome"));

        // when
        List<NotificationView> views = factory.toViews(records, UserRole.ADMIN);

        // then
        assertThat(views).extracting(NotificationView::warning).containsExactly(true, false);
        assertThat(views).extracting(NotificationView::cssClass).containsExactly("is-warn", "is-info");
        assertThat(views).extracting(NotificationView::icon).containsExactly("fa-exclamation-triangle", "fa-info-circle");
        assertThat(views).allMatch(view -> view.actionHref() == null && view.actionKey() == null);
    }

    @Test
    void keepsTheOriginalMessageAndUsesAGenericTitleForAnUnknownType() {
        // given
        StoreNotificationRecord record = record(null, null, null, "Something happened");

        // when
        NotificationView view = factory.toView(record, UserRole.ADMIN);

        // then
        assertThat(view.titleKey()).isEqualTo("store.notification.type.default");
        assertThat(view.message()).isEqualTo("Something happened");
        assertThat(view.warning()).isFalse();
    }

    @Test
    void formatsTheCreationDateAndReportsAReadNotificationAsRead() {
        // given
        StoreNotificationRecord record = record(StoreNotificationSeverity.WARNING,
                StoreNotificationType.MARKETPLACE_RETURN_UNMATCHED, "ret-1", "Unmatched");
        record.setReadAt(CREATED_AT.plusHours(1));
        record.setUnreadStoreId(null);

        // when
        NotificationView view = factory.toView(record, UserRole.ADMIN);

        // then
        assertThat(view.unread()).isFalse();
        assertThat(view.createdAtText()).isEqualTo("14.09.2026 15:32");
    }

    @Test
    void encodesTheNotificationIdAsOnePathSegmentOfTheOpenLink() {
        // given
        StoreNotificationRecord record = record(StoreNotificationSeverity.WARNING,
                StoreNotificationType.MARKETPLACE_RETURN_UNMATCHED, "REF 1", "Unmatched");

        // when
        NotificationView view = factory.toView(record, UserRole.ADMIN);

        // then
        assertThat(view.id()).isEqualTo("MARKETPLACE_RETURN_UNMATCHED:REF 1");
        assertThat(view.openHref()).isEqualTo("/dashboard/notifications/MARKETPLACE_RETURN_UNMATCHED:REF%201/open");
    }

    @Test
    void translatesEveryNotificationTitleAndActionInBothLanguages() {
        for (String language : List.of("pl", "en")) {
            // given
            ResourceBundle messages = ResourceBundle.getBundle("messages", Locale.forLanguageTag(language));
            List<String> keys = new ArrayList<>(List.of("store.notification.type.default",
                    "store.notification.action.reconnect", "store.notification.action.viewReturn"));
            for (StoreNotificationType type : StoreNotificationType.values()) {
                keys.add("store.notification.type." + type.name());
            }

            // when / then
            keys.forEach(key -> assertThat(messages.containsKey(key)).as(language + " " + key).isTrue());
        }
    }
}
