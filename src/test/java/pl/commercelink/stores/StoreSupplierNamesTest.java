package pl.commercelink.stores;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoreSupplierNamesTest {

    @Test
    void returnsPricingEnabledNamesForGivenMode() {
        // given
        Store store = storeWith(
                new StoreSupplierConnection("AbGroup", ConnectionMode.GLOBAL, true, false),
                new StoreSupplierConnection("Action", ConnectionMode.GLOBAL, false, true),
                new StoreSupplierConnection("Senetic", ConnectionMode.OWN, true, true));

        // when
        List<String> pricingGlobal = store.supplierNames(ConnectionMode.GLOBAL, SupplierScope.PRICING);

        // then
        assertThat(pricingGlobal).containsExactly("AbGroup");
    }

    @Test
    void returnsFulfilmentEnabledNamesForGivenMode() {
        // given
        Store store = storeWith(
                new StoreSupplierConnection("AbGroup", ConnectionMode.GLOBAL, true, false),
                new StoreSupplierConnection("Action", ConnectionMode.GLOBAL, false, true),
                new StoreSupplierConnection("Senetic", ConnectionMode.OWN, true, true));

        // when
        List<String> fulfilmentGlobal = store.supplierNames(ConnectionMode.GLOBAL, SupplierScope.FULFILMENT);
        List<String> fulfilmentOwn = store.supplierNames(ConnectionMode.OWN, SupplierScope.FULFILMENT);

        // then
        assertThat(fulfilmentGlobal).containsExactly("Action");
        assertThat(fulfilmentOwn).containsExactly("Senetic");
    }

    @Test
    void returnsEmptyWhenNoFulfilmentConfiguration() {
        // given
        Store store = new Store();

        // when / then
        assertThat(store.supplierNames(ConnectionMode.GLOBAL, SupplierScope.PRICING)).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoConnections() {
        // given
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(List.of());
        Store store = new Store();
        store.setFulfilmentConfiguration(config);

        // when / then
        assertThat(store.supplierNames(ConnectionMode.OWN, SupplierScope.FULFILMENT)).isEmpty();
    }

    private Store storeWith(StoreSupplierConnection... connections) {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(List.of(connections));
        Store store = new Store();
        store.setFulfilmentConfiguration(config);
        return store;
    }
}
