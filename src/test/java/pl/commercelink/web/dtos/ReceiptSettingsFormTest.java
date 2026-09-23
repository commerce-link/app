package pl.commercelink.web.dtos;

import org.junit.jupiter.api.Test;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.notifications.EmailNotificationType;
import pl.commercelink.stores.Store;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptSettingsFormTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 23, 13, 0);

    @Test
    void enablingNeedsAtLeastOneSource() {
        ReceiptSettingsForm form = new ReceiptSettingsForm();
        form.setEnabled(true);
        form.setSources(List.of());

        assertThat(form.validate()).containsKey("sources");
    }

    @Test
    void enablingStampsTheMomentAndSwitchesTheEmailOn() {
        Store store = new Store();
        ReceiptSettingsForm form = new ReceiptSettingsForm();
        form.setEnabled(true);
        form.setSources(List.of("WebStore", "Marketplace"));

        form.applyTo(store, NOW);

        assertThat(store.getReceiptConfiguration().isEnabled()).isTrue();
        assertThat(store.getReceiptConfiguration().getEnabledAt()).isEqualTo(NOW);
        assertThat(store.getReceiptConfiguration().covers(OrderSourceType.Marketplace)).isTrue();
        assertThat(store.getReceiptConfiguration().covers(OrderSourceType.CallCenter)).isFalse();
        assertThat(store.getClientNotificationsConfiguration().supports(EmailNotificationType.ORDER_RECEIPT)).isTrue();
    }

    @Test
    void aStoreThatTurnedTheEmailOffKeepsItOff() {
        Store store = new Store();
        ReceiptSettingsForm form = new ReceiptSettingsForm();
        form.setEnabled(true);
        form.setSources(List.of("WebStore"));
        form.applyTo(store, NOW);
        store.getClientNotificationsConfiguration().disableNotification(EmailNotificationType.ORDER_RECEIPT);
        store.getReceiptConfiguration().disable();

        form.applyTo(store, NOW.plusDays(1));

        assertThat(store.getClientNotificationsConfiguration().supports(EmailNotificationType.ORDER_RECEIPT)).isFalse();
    }

    @Test
    void unknownSourcesAreRefused() {
        ReceiptSettingsForm form = new ReceiptSettingsForm();
        form.setEnabled(true);
        form.setSources(List.of("Telepathy"));

        assertThat(form.validate()).containsKey("sources");
    }
}
