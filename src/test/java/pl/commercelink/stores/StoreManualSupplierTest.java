package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreManualSupplierTest {

    private Store storeWith(StoreSupplierConnection... connections) {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(List.of(connections));
        Store store = new Store();
        store.setFulfilmentConfiguration(config);
        return store;
    }

    @Test
    void manualNamesAreSeparateFromOwnAndGlobal() {
        // given
        Store store = storeWith(
                new StoreSupplierConnection("Acme", ConnectionMode.GLOBAL),
                new StoreSupplierConnection("Also", ConnectionMode.OWN),
                new StoreSupplierConnection("manual:H1", ConnectionMode.MANUAL));

        // when / then
        assertEquals(List.of("manual:H1"), store.getManualSupplierNames());
        assertEquals(List.of("Also"), store.getOwnSupplierNames());
        assertFalse(store.getOwnSupplierNames().contains("manual:H1"));
        assertTrue(store.getOwnAndManualSupplierNames().containsAll(List.of("Also", "manual:H1")));
        assertTrue(store.hasOwnOrManualSupplierConnections());
    }

    @Test
    void scopeFiltersManualByFlags() {
        // given
        Store store = storeWith(
                new StoreSupplierConnection("manual:H1", ConnectionMode.MANUAL, true, false),
                new StoreSupplierConnection("manual:H2", ConnectionMode.MANUAL, false, true),
                new StoreSupplierConnection("Also", ConnectionMode.OWN, true, true));

        // when / then
        assertTrue(store.ownAndManualSupplierNames(SupplierScope.PRICING).containsAll(List.of("manual:H1", "Also")));
        assertFalse(store.ownAndManualSupplierNames(SupplierScope.PRICING).contains("manual:H2"));
        assertTrue(store.ownAndManualSupplierNames(SupplierScope.FULFILMENT).containsAll(List.of("manual:H2", "Also")));
        assertFalse(store.ownAndManualSupplierNames(SupplierScope.FULFILMENT).contains("manual:H1"));
    }
}
