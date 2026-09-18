package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;

import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationOverviewTest {

    private Store storeWith(EmailNotificationType... enabled) {
        Store store = new Store();
        ClientNotificationsConfiguration configuration = new ClientNotificationsConfiguration();
        Arrays.stream(enabled).forEach(type -> configuration.enableNotification(type, type.getTemplateName()));
        store.setClientNotificationsConfiguration(configuration);
        return store;
    }

    private List<NotificationOverview.Item> items(NotificationOverview overview) {
        return overview.groups().stream().flatMap(group -> group.items().stream()).toList();
    }

    @Test
    void listsEveryNotificationTypeExactlyOnceInGroups() {
        // when
        NotificationOverview overview = NotificationOverview.of(storeWith(), "/dashboard/store/email-templates");

        // then
        assertThat(items(overview)).extracting(NotificationOverview.Item::type)
                .containsExactlyInAnyOrder(EmailNotificationType.values());
        assertThat(overview.groups()).extracting(NotificationOverview.Group::labelKey).containsExactly(
                "email.notification.group.orders", "email.notification.group.invoices",
                "email.notification.group.returns", "email.notification.group.clientVerification");
    }

    @Test
    void marksTheEnabledTypesAndCountsThem() {
        // when
        NotificationOverview overview = NotificationOverview.of(
                storeWith(EmailNotificationType.ORDER_CONFIRMATION, EmailNotificationType.RMA_REJECTED), "/dashboard/store/email-templates");

        // then
        assertThat(overview.enabledCount()).isEqualTo(2);
        assertThat(overview.totalCount()).isEqualTo(EmailNotificationType.values().length);
        assertThat(items(overview)).filteredOn(NotificationOverview.Item::enabled).extracting(NotificationOverview.Item::type)
                .containsExactlyInAnyOrder(EmailNotificationType.ORDER_CONFIRMATION, EmailNotificationType.RMA_REJECTED);
    }

    @Test
    void linksEachTypeToItsTemplate() {
        // when
        NotificationOverview overview = NotificationOverview.of(storeWith(), "/dashboard/store/store-9/email-templates");

        // then
        assertThat(items(overview)).filteredOn(item -> item.type() == EmailNotificationType.ORDER_SHIPPING).singleElement()
                .extracting(NotificationOverview.Item::templateHref)
                .isEqualTo("/dashboard/store/store-9/email-templates?selectedType=ORDER_SHIPPING");
    }

    @Test
    void namesTheTypesTheCustomerAddressChangeNeeds() {
        // when
        NotificationOverview overview = NotificationOverview.of(storeWith(), "/dashboard/store/email-templates");

        // then
        assertThat(items(overview)).filteredOn(NotificationOverview.Item::requiredForAddressChange)
                .extracting(NotificationOverview.Item::type)
                .containsExactlyInAnyOrder(EmailNotificationType.CLIENT_VERIFICATION_CODE, EmailNotificationType.ORDER_SHIPPING_ADDRESS_CHANGED);
    }

    @Test
    void warnsWhenCustomerAddressChangeIsOnButARequiredTypeIsOff() {
        // given
        Store store = storeWith(EmailNotificationType.CLIENT_VERIFICATION_CODE);
        FulfilmentConfiguration fulfilment = new FulfilmentConfiguration();
        fulfilment.setClientShippingAddressChangeEnabled(true);
        store.setFulfilmentConfiguration(fulfilment);

        // when / then
        assertThat(NotificationOverview.of(store, "/x").addressChangeBlocked()).isTrue();
    }

    @Test
    void doesNotWarnWhenCustomerAddressChangeIsOff() {
        // when / then
        assertThat(NotificationOverview.of(storeWith(), "/x").addressChangeBlocked()).isFalse();
    }

    @Test
    void everyTypeAndGroupHasAPolishAndEnglishName() {
        // given
        NotificationOverview overview = NotificationOverview.of(storeWith(), "/x");

        // when / then
        for (Locale locale : List.of(Locale.forLanguageTag("pl"), Locale.ENGLISH)) {
            ResourceBundle messages = ResourceBundle.getBundle("messages", locale);
            overview.groups().forEach(group -> {
                assertThat(messages.containsKey(group.labelKey())).as(group.labelKey()).isTrue();
                group.items().forEach(item -> assertThat(messages.containsKey(item.labelKey())).as(item.labelKey()).isTrue());
            });
        }
    }
}
