package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.invoicing.InvoicingProviderFactory;
import pl.commercelink.payments.PaymentProviderFactory;
import pl.commercelink.shipping.ShippingProviderFactory;
import pl.commercelink.starter.security.UserRole;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreNotification;
import pl.commercelink.stores.StoreNotificationSeverity;
import pl.commercelink.stores.StoreNotificationType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class StoreSettingsOverviewFactoryAlertsTest {

    @Mock
    private InvoicingProviderFactory invoicingProviderFactory;

    @Mock
    private PaymentProviderFactory paymentProviderFactory;

    @Mock
    private ShippingProviderFactory shippingProviderFactory;

    @InjectMocks
    private StoreSettingsOverviewFactory factory;

    private Store storeWith(StoreNotification... notifications) {
        Store store = new Store();
        store.setStoreId("store-1");
        store.setNotifications(new ArrayList<>(List.of(notifications)));
        return store;
    }

    private StoreNotification notification(StoreNotificationSeverity severity, StoreNotificationType type, String object) {
        return new StoreNotification(severity, type, object, "Original message for " + type);
    }

    @Test
    void turnsAnExpiredMarketplaceConnectionIntoAWarningWithAReconnectLink() {
        // given
        Store store = storeWith(notification(StoreNotificationSeverity.WARNING, StoreNotificationType.UNAUTHENTICATED,
                "allegro_marketplace"));

        // when
        List<StoreAlert> adminAlerts = factory.build(store, UserRole.ADMIN).alerts();
        List<StoreAlert> superAdminAlerts = factory.build(store, UserRole.SUPER_ADMIN).alerts();

        // then
        assertThat(adminAlerts).containsExactly(new StoreAlert(true, "store.notification.type.UNAUTHENTICATED",
                "Original message for UNAUTHENTICATED", "/dashboard/store/marketplaces",
                "store.notification.action.reconnect"));
        assertThat(superAdminAlerts.get(0).actionHref()).isEqualTo("/dashboard/store/store-1/marketplaces");
    }

    @Test
    void linksARefundedReturnToItsRmaForTheStoreAdminOnly() {
        // given
        Store store = storeWith(
                notification(StoreNotificationSeverity.WARNING, StoreNotificationType.MARKETPLACE_RETURN_REFUNDED, "rma-7"),
                notification(StoreNotificationSeverity.WARNING, StoreNotificationType.MARKETPLACE_RETURN_REFUNDED, " "));

        // when
        List<StoreAlert> adminAlerts = factory.build(store, UserRole.ADMIN).alerts();
        List<StoreAlert> superAdminAlerts = factory.build(store, UserRole.SUPER_ADMIN).alerts();

        // then
        assertThat(adminAlerts.get(0).actionHref()).isEqualTo("/dashboard/rma/rma-7");
        assertThat(adminAlerts.get(0).actionKey()).isEqualTo("store.notification.action.viewReturn");
        assertThat(adminAlerts.get(1).actionHref()).isNull();
        // the RMA screen is scoped to the store of the logged-in admin, so the super admin would land elsewhere
        assertThat(superAdminAlerts).allMatch(alert -> alert.actionHref() == null && alert.actionKey() == null);
    }

    @Test
    void showsOtherNotificationsWithoutAnActionAndInfoSeverityAsInfo() {
        // given
        Store store = storeWith(
                notification(StoreNotificationSeverity.WARNING, StoreNotificationType.MARKETPLACE_RETURN_UNMATCHED, "ret-1"),
                notification(StoreNotificationSeverity.INFO, StoreNotificationType.WELCOME, null));

        // when
        List<StoreAlert> alerts = factory.build(store, UserRole.ADMIN).alerts();

        // then
        assertThat(alerts).extracting(StoreAlert::warning).containsExactly(true, false);
        assertThat(alerts).extracting(StoreAlert::cssClass).containsExactly("is-warn", "is-info");
        assertThat(alerts).extracting(StoreAlert::icon).containsExactly("fa-exclamation-triangle", "fa-info-circle");
        assertThat(alerts).allMatch(alert -> alert.actionHref() == null);
    }

    @Test
    void keepsTheOriginalMessageAndUsesAGenericTitleForAnUnknownType() {
        // given
        Store store = storeWith(new StoreNotification(null, null, null, "Something happened"));

        // when
        StoreAlert alert = factory.build(store, UserRole.ADMIN).alerts().get(0);

        // then
        assertThat(alert.titleKey()).isEqualTo("store.notification.type.default");
        assertThat(alert.message()).isEqualTo("Something happened");
        assertThat(alert.warning()).isFalse();
    }

    @Test
    void buildsNoAlertsWhenTheNotificationListIsMissing() {
        // given
        Store store = storeWith();
        store.setNotifications(null);

        // when / then
        assertThat(factory.build(store, UserRole.ADMIN).alerts()).isEmpty();
    }

    @Test
    void translatesEveryNotificationTitleAndActionInBothLanguages() {
        for (String language : List.of("pl", "en")) {
            // given
            ResourceBundle messages = ResourceBundle.getBundle("messages", Locale.forLanguageTag(language));
            List<String> keys = new ArrayList<>(List.of("store.notification.type.default",
                    "store.notification.action.reconnect", "store.notification.action.viewReturn",
                    "store.notification.alerts.aria"));
            for (StoreNotificationType type : StoreNotificationType.values()) {
                keys.add("store.notification.type." + type.name());
            }

            // when / then
            keys.forEach(key -> assertThat(messages.containsKey(key)).as(language + " " + key).isTrue());
        }
    }
}
