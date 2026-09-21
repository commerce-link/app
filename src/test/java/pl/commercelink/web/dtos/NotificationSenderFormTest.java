package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.ClientNotificationsConfiguration;
import pl.commercelink.stores.Store;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSenderFormTest {

    private NotificationSenderForm form(String senderName, String replyToEmail) {
        NotificationSenderForm form = new NotificationSenderForm();
        form.setSenderName(senderName);
        form.setReplyToEmail(replyToEmail);
        return form;
    }

    @Test
    void bothFieldsAreOptional() {
        // when / then
        assertThat(form(null, "  ").validate()).isEmpty();
    }

    @Test
    void rejectsAReplyToAddressThatIsNotAnEmail() {
        // when
        Map<String, String> errors = form("Sklep Demo", "kontakt.sklep-demo.pl").validate();

        // then
        assertThat(errors).containsExactly(Map.entry("replyToEmail", "store.notification.replyToEmail.invalid"));
    }

    @Test
    void rejectsSenderNameCharactersThatWouldBreakTheFromHeader() {
        // when / then
        assertThat(form("Sklep \"Demo\"", null).validate()).containsEntry("senderName", "store.notification.senderName.invalid");
        assertThat(form("Sklep <Demo>", null).validate()).containsEntry("senderName", "store.notification.senderName.invalid");
        assertThat(form("Sklep\r\nBcc: x@example.com", null).validate()).containsEntry("senderName", "store.notification.senderName.invalid");
    }

    @Test
    void rejectsAnOverlongSenderName() {
        // when / then
        assertThat(form("S".repeat(101), null).validate()).containsEntry("senderName", "store.notification.senderName.too.long");
        assertThat(form("S".repeat(100), null).validate()).isEmpty();
    }

    @Test
    void savesTrimmedValuesAndBlankAsNoneKeepingTheEnabledTemplates() {
        // given
        Store store = new Store();
        ClientNotificationsConfiguration configuration = new ClientNotificationsConfiguration();
        configuration.enableNotification(EmailNotificationType.ORDER_SHIPPING, "OrderShippingTemplate");
        configuration.setSenderName("Old");
        store.setClientNotificationsConfiguration(configuration);

        // when
        form("  Sklep Demo  ", "   ").applyTo(store);

        // then
        assertThat(store.getClientNotificationsConfiguration().getSenderName()).isEqualTo("Sklep Demo");
        assertThat(store.getClientNotificationsConfiguration().getReplyToEmail()).isNull();
        assertThat(store.supportsNotification(EmailNotificationType.ORDER_SHIPPING)).isTrue();
    }

    @Test
    void createsTheConfigurationOfAStoreThatHasNone() {
        // given
        Store store = new Store();

        // when
        form("Sklep Demo", "kontakt@sklep-demo.pl").applyTo(store);

        // then
        assertThat(store.getClientNotificationsConfiguration().getReplyToEmail()).isEqualTo("kontakt@sklep-demo.pl");
    }

    @Test
    void startsFromTheStoredValues() {
        // given
        Store store = new Store();
        ClientNotificationsConfiguration configuration = new ClientNotificationsConfiguration();
        configuration.setSenderName("Sklep Demo");
        configuration.setReplyToEmail("kontakt@sklep-demo.pl");
        store.setClientNotificationsConfiguration(configuration);

        // when
        NotificationSenderForm form = NotificationSenderForm.from(store);

        // then
        assertThat(form.getSenderName()).isEqualTo("Sklep Demo");
        assertThat(form.getReplyToEmail()).isEqualTo("kontakt@sklep-demo.pl");
    }
}
