package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreSupplierModeNamesTest {

    private Store storeWithConnections(StoreSupplierConnection... connections) {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(List.of(connections));
        Store store = new Store();
        store.setFulfilmentConfiguration(config);
        return store;
    }

    @Test
    void partitionsSupplierNamesByConnectionMode() {
        // given
        Store store = storeWithConnections(
                new StoreSupplierConnection("Action", ConnectionMode.GLOBAL),
                new StoreSupplierConnection("Wortmann", ConnectionMode.OWN),
                new StoreSupplierConnection("Elko", ConnectionMode.OWN));

        // when / then
        assertEquals(List.of("Action"), store.getGlobalSupplierNames());
        assertEquals(List.of("Wortmann", "Elko"), store.getOwnSupplierNames());
    }

    @Test
    void hasOwnSupplierConnectionsReflectsOwnModeConnections() {
        // given
        Store withOwn = storeWithConnections(new StoreSupplierConnection("Wortmann", ConnectionMode.OWN));
        Store globalOnly = storeWithConnections(new StoreSupplierConnection("Action", ConnectionMode.GLOBAL));

        // when / then
        assertTrue(withOwn.hasOwnSupplierConnections());
        assertFalse(globalOnly.hasOwnSupplierConnections());
    }

    @Test
    void returnsEmptyWhenFulfilmentConfigurationMissing() {
        // given
        Store store = new Store();

        // when / then
        assertTrue(store.getOwnSupplierNames().isEmpty());
        assertTrue(store.getGlobalSupplierNames().isEmpty());
        assertFalse(store.hasOwnSupplierConnections());
    }
}
