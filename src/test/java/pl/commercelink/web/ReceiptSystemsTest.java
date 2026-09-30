package pl.commercelink.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.receipts.FakeReceiptProviderDescriptor;
import pl.commercelink.receipts.ReceiptAttemptStore;
import pl.commercelink.receipts.ReceiptProviderFactory;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link ReceiptSystems#disconnect(Store, String)}: switching automatic receipts off so a later reconnect cannot
 *  fiscalise, on its next save, orders delivered while the store had no e-receipt system. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReceiptSystemsTest {

    private static final String SYSTEM = FakeReceiptProviderDescriptor.NAME;
    private static final String API_DOMAIN = "https://api.test";

    @Mock
    private ReceiptProviderFactory receiptProviderFactory;
    @Mock
    private ReceiptAttemptStore attempts;

    private ReceiptSystems receiptSystems;

    @BeforeEach
    void setUp() {
        FakeReceiptProviderDescriptor descriptor = new FakeReceiptProviderDescriptor();
        when(receiptProviderFactory.getDescriptor(SYSTEM)).thenReturn(descriptor);
        when(receiptProviderFactory.availableProviders()).thenReturn(List.of(descriptor));
        receiptSystems = new ReceiptSystems(receiptProviderFactory, attempts, API_DOMAIN);
    }

    @Test
    void disconnectSwitchesAutomaticReceiptsOff() {
        // given
        Store store = configuredStore();
        store.getReceiptConfiguration().enable(LocalDateTime.of(2026, 1, 1, 10, 0));

        // when
        receiptSystems.disconnect(store, SYSTEM);

        // then
        verify(receiptProviderFactory).deleteConfiguration(store, SYSTEM);
        assertThat(store.getConfigurationValue(IntegrationType.RECEIPT_PROVIDER)).isNull();
        assertThat(store.getReceiptConfiguration().isEnabled()).isFalse();
    }

    /** Reconnecting and switching e-receipts back on must set a fresh enabledAt: {@code enable} only moves it while
     *  receipts are off, so without the disconnect disabling them first, orders delivered during the disconnection
     *  (after the old enabledAt) would be fiscalised retroactively on their next save. */
    @Test
    void reconnectingAndEnablingAgainGetsAFreshEnabledAt() {
        // given
        Store store = configuredStore();
        LocalDateTime firstEnable = LocalDateTime.of(2026, 1, 1, 10, 0);
        store.getReceiptConfiguration().enable(firstEnable);

        // when: disconnect, then reconnect to the same system, then switch e-receipts back on
        receiptSystems.disconnect(store, SYSTEM);
        receiptSystems.save(store, SYSTEM, Map.of("token", "secret"));
        LocalDateTime reconnectEnable = LocalDateTime.of(2026, 3, 1, 9, 0);
        store.getReceiptConfiguration().enable(reconnectEnable);

        // then
        assertThat(store.getReceiptConfiguration().isEnabled()).isTrue();
        assertThat(store.getReceiptConfiguration().getEnabledAt()).isEqualTo(reconnectEnable).isNotEqualTo(firstEnable);
    }

    private Store configuredStore() {
        Store store = new Store();
        store.setStoreId("store-1");
        store.setConfigurationValue(IntegrationType.RECEIPT_PROVIDER, SYSTEM);
        when(receiptProviderFactory.loadConfigurationForUI(store)).thenReturn(Map.of());
        return store;
    }
}
