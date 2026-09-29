package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.Store;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptSettingsFormTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 13, 0);

    @Test
    void enablingStampsTheMomentAndSwitchesTheEmailOn() {
        Store store = new Store();
        ReceiptSettingsForm form = new ReceiptSettingsForm();
        form.setEnabled(true);

        form.applyTo(store, NOW);

        assertThat(store.getReceiptConfiguration().isEnabled()).isTrue();
        assertThat(store.getReceiptConfiguration().getEnabledAt()).isEqualTo(NOW);
        assertThat(store.getClientNotificationsConfiguration().supports(EmailNotificationType.ORDER_RECEIPT)).isTrue();
    }

    @Test
    void aStoreThatTurnedTheEmailOffKeepsItOff() {
        Store store = new Store();
        ReceiptSettingsForm form = new ReceiptSettingsForm();
        form.setEnabled(true);
        form.applyTo(store, NOW);
        store.getClientNotificationsConfiguration().disableNotification(EmailNotificationType.ORDER_RECEIPT);
        store.getReceiptConfiguration().disable();

        form.applyTo(store, NOW.plusDays(1));

        assertThat(store.getClientNotificationsConfiguration().supports(EmailNotificationType.ORDER_RECEIPT)).isFalse();
    }

    @Test
    void disablingTurnsTheConfigurationOff() {
        Store store = new Store();
        ReceiptSettingsForm form = new ReceiptSettingsForm();
        form.setEnabled(true);
        form.applyTo(store, NOW);

        form.setEnabled(false);
        form.applyTo(store, NOW.plusDays(1));

        assertThat(store.getReceiptConfiguration().isEnabled()).isFalse();
    }
}
