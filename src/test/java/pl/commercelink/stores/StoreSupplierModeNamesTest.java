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
        Store store = storeWithConnections(
                new StoreSupplierConnection("Action", ConnectionMode.GLOBAL),
                new StoreSupplierConnection("Wortmann", ConnectionMode.OWN),
                new StoreSupplierConnection("Elko", ConnectionMode.OWN));

        assertEquals(List.of("Action"), store.getGlobalSupplierNames());
        assertEquals(List.of("Wortmann", "Elko"), store.getOwnSupplierNames());
    }

    @Test
    void hasOwnSupplierConnectionsReflectsOwnModeConnections() {
        Store withOwn = storeWithConnections(new StoreSupplierConnection("Wortmann", ConnectionMode.OWN));
        Store globalOnly = storeWithConnections(new StoreSupplierConnection("Action", ConnectionMode.GLOBAL));

        assertTrue(withOwn.hasOwnSupplierConnections());
        assertFalse(globalOnly.hasOwnSupplierConnections());
    }

    @Test
    void returnsEmptyWhenFulfilmentConfigurationMissing() {
        Store store = new Store();

        assertTrue(store.getOwnSupplierNames().isEmpty());
        assertTrue(store.getGlobalSupplierNames().isEmpty());
        assertFalse(store.hasOwnSupplierConnections());
    }
}
