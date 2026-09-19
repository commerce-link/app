package pl.commercelink.web.settings;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.templates.EmailTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationOverviewTest {

    private Store storeWith(EmailNotificationType... enabled) {
        Store store = new Store();
        ClientNotificationsConfiguration configuration = new ClientNotificationsConfiguration();
        Arrays.stream(enabled).forEach(type -> configuration.enableNotification(type, type.getTemplateName()));
        store.setClientNotificationsConfiguration(configuration);
        return store;
    }

    private static EmailTemplate template(EmailNotificationType type, String subject, String body) {
        EmailTemplate template = new EmailTemplate();
        template.setTemplateName(type.getTemplateName());
        template.setType(type);
        template.setSubject(subject);
        template.setTextBody(body);
        return template;
    }

    /** Every type has default content, as the shared pool on production does. */
    private static List<EmailTemplate> defaultsForEveryType() {
        return Arrays.stream(EmailNotificationType.values()).map(type -> template(type, "Temat", "Treść")).toList();
    }

    private static NotificationOverview overview(Store store, List<EmailTemplate> own, List<EmailTemplate> defaults) {
        List<EmailTemplateView> emails = EmailTemplateView.forStore(store.getClientNotificationsConfiguration(), own,
                        defaults, "/x").stream().flatMap(group -> group.items().stream()).toList();
        return NotificationOverview.of(store, emails);
    }

    @Test
    void countsTheEnabledEmailsThatHaveContentAsSent() {
        // when
        NotificationOverview overview = overview(storeWith(EmailNotificationType.ORDER_CONFIRMATION,
                EmailNotificationType.RMA_REJECTED), List.of(), defaultsForEveryType());

        // then
        assertThat(overview.sentCount()).isEqualTo(2);
        assertThat(overview.totalCount()).isEqualTo(EmailNotificationType.values().length);
        assertThat(overview.brokenCount()).isZero();
    }

    @Test
    void anEnabledEmailWhoseCopyHasNoBodyIsNotCountedAsSent() {
        // when
        NotificationOverview overview = overview(storeWith(EmailNotificationType.ORDER_ASSEMBLY),
                List.of(template(EmailNotificationType.ORDER_ASSEMBLY, "Temat", null)), defaultsForEveryType());

        // then
        assertThat(overview.sentCount()).isZero();
        assertThat(overview.brokenCount()).isEqualTo(1);
    }

    @Test
    void warnsWhenCustomerAddressChangeIsOnButARequiredTypeIsOff() {
        // given
        Store store = storeWith(EmailNotificationType.CLIENT_VERIFICATION_CODE);
        FulfilmentConfiguration fulfilment = new FulfilmentConfiguration();
        fulfilment.setClientShippingAddressChangeEnabled(true);
        store.setFulfilmentConfiguration(fulfilment);

        // when / then
        assertThat(overview(store, List.of(), defaultsForEveryType()).addressChangeBlocked()).isTrue();
    }

    @Test
    void warnsWhenARequiredTypeIsOnButHasNoContent() {
        // given
        Store store = storeWith(EmailNotificationType.CLIENT_VERIFICATION_CODE, EmailNotificationType.ORDER_SHIPPING_ADDRESS_CHANGED);
        FulfilmentConfiguration fulfilment = new FulfilmentConfiguration();
        fulfilment.setClientShippingAddressChangeEnabled(true);
        store.setFulfilmentConfiguration(fulfilment);

        // when / then
        assertThat(overview(store, List.of(), List.of()).addressChangeBlocked()).isTrue();
        assertThat(overview(store, List.of(), defaultsForEveryType()).addressChangeBlocked()).isFalse();
    }

    @Test
    void doesNotWarnWhenCustomerAddressChangeIsOff() {
        // when / then
        assertThat(overview(storeWith(), List.of(), List.of()).addressChangeBlocked()).isFalse();
    }

    @Test
    void everyTypeAndGroupHasAPolishAndEnglishName() {
        // when / then
        for (Locale locale : List.of(Locale.forLanguageTag("pl"), Locale.ENGLISH)) {
            ResourceBundle messages = ResourceBundle.getBundle("messages", locale);
            NotificationOverview.GROUPS.forEach(group -> {
                assertThat(messages.containsKey(group.labelKey())).as(group.labelKey()).isTrue();
                group.types().forEach(type -> assertThat(messages.containsKey(EmailTemplateView.labelKey(type)))
                        .as(type.name()).isTrue());
            });
        }
    }
}
