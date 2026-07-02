package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.SupplierScope;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryScopeManualTest {

    @Test
    void ownGroupScopeIncludesManualByFlag() {
        // given
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(List.of(
                new StoreSupplierConnection("manual:H1", ConnectionMode.MANUAL, true, false),
                new StoreSupplierConnection("Also", ConnectionMode.OWN, true, true)));
        Store store = new Store();
        store.setFulfilmentConfiguration(config);

        // when
        List<String> pricing = store.ownAndManualSupplierNames(SupplierScope.PRICING);

        // then
        assertTrue(pricing.containsAll(List.of("manual:H1", "Also")));
    }
}
