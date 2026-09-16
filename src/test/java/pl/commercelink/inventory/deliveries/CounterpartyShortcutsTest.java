package pl.commercelink.inventory.deliveries;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CounterpartyShortcutsTest {

    private final CounterpartyShortcuts shortcuts = new CounterpartyShortcuts();

    private Store store(StoreSupplierConnection... connections) {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(new ArrayList<>(List.of(connections)));
        Store store = new Store();
        store.setStoreId("s1");
        store.setFulfilmentConfiguration(config);
        return store;
    }

    private Delivery delivery(String provider) {
        return new Delivery("s1", "EXT-1", provider);
    }

    @Test
    void syncedShortcutWinsOverEverything() {
        // given
        Delivery delivery = delivery("Kosatec-k7f3a9c2");
        delivery.setCounterpartyShortcut("KOS-INV");

        // when / then
        assertThat(shortcuts.forDelivery(store(), delivery)).isEqualTo("KOS-INV");
    }

    @Test
    void connectionBillingShortcutIsUsedNext() {
        // given
        StoreSupplierConnection connection = new StoreSupplierConnection("Kosatec-k7f3a9c2", ConnectionMode.OWN);
        connection.setBillingShortcut("KOSATEC-B");

        // when / then
        assertThat(shortcuts.forDelivery(store(connection), delivery("Kosatec-k7f3a9c2"))).isEqualTo("KOSATEC-B");
    }

    @Test
    void tokenedIdentityDefaultsToItsTypeAndManualToItsLabel() {
        // given
        StoreSupplierConnection own = new StoreSupplierConnection("Kosatec-k7f3a9c2", ConnectionMode.OWN);
        StoreSupplierConnection manual = new StoreSupplierConnection("manual-k7f3a9c2", ConnectionMode.MANUAL);
        manual.setLabel("Asus");
        Store store = store(own, manual);

        // when / then
        assertThat(shortcuts.forDelivery(store, delivery("Kosatec-k7f3a9c2"))).isEqualTo("Kosatec");
        assertThat(shortcuts.forDelivery(store, delivery("manual-k7f3a9c2"))).isEqualTo("Asus");
    }

    @Test
    void legacyIdentitiesKeepTodaysBehaviourOfUsingTheProviderVerbatim() {
        // given
        Store store = store(new StoreSupplierConnection("Kosatec", ConnectionMode.OWN),
                new StoreSupplierConnection("manual:Asus", ConnectionMode.MANUAL));

        // when / then
        assertThat(shortcuts.forDelivery(store, delivery("Kosatec"))).isEqualTo("Kosatec");
        assertThat(shortcuts.forDelivery(store, delivery("manual:Asus"))).isEqualTo("manual:Asus");
        // a delivery whose provider was overwritten by an old invoice sync
        assertThat(shortcuts.forDelivery(store, delivery("KOS-INV"))).isEqualTo("KOS-INV");
        assertThat(shortcuts.forDelivery(null, delivery("Kosatec"))).isEqualTo("Kosatec");
    }
}
