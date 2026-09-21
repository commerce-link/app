package pl.commercelink.web.dtos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.commercelink.orders.Order;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoutedSupplierViewTest {


    private static Store storeWith(StoreSupplierConnection... connections) {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(new ArrayList<>(List.of(connections)));
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(config);
        return store;
    }

    private static StoreSupplierConnection connection(String name, ConnectionMode mode, String externalSupplierId) {
        StoreSupplierConnection connection = new StoreSupplierConnection(name, mode, true, true);
        connection.setExternalSupplierId(externalSupplierId);
        return connection;
    }

    private static Order order(String externalSupplierId) {
        Order order = new Order("store-1");
        order.setOrderId("order-1");
        order.setExternalSupplierId(externalSupplierId);
        return order;
    }


    @Test
    @DisplayName("an order the marketplace did not route has no routed supplier to show")
    void nothingToShowForUnroutedOrder() {
        assertThat(RoutedSupplierView.from(order(null), storeWith())).isNull();
    }

    @Test
    @DisplayName("a routed order shows the supplier connection carrying its id")
    void showsTheMatchingSupplier() {
        Store store = storeWith(connection("Acme", ConnectionMode.OWN, "2"), connection("Bravo", ConnectionMode.OWN, "7"));

        RoutedSupplierView view = RoutedSupplierView.from(order("2"), store);

        assertThat(view.isMatched()).isTrue();
        assertThat(view.supplierName()).isEqualTo("Acme");
        assertThat(view.modeKey()).isEqualTo("inventory.provider.own");
        assertThat(view.enabled()).isTrue();
        assertThat(view.includeInFulfilment()).isTrue();
    }

    @Test
    @DisplayName("a routed order with no supplier configured for its id still shows the id")
    void showsTheIdWhenNoSupplierMatches() {
        RoutedSupplierView view = RoutedSupplierView.from(order("9"), storeWith(connection("Acme", ConnectionMode.OWN, "2")));

        assertThat(view.isMatched()).isFalse();
        assertThat(view.externalSupplierId()).isEqualTo("9");
        assertThat(view.supplierName()).isNull();
    }

    @Test
    @DisplayName("a GLOBAL connection carrying the id is ignored: global suppliers never route")
    void ignoresAGlobalConnectionCarryingTheId() {
        RoutedSupplierView view = RoutedSupplierView.from(order("2"), storeWith(connection("Acme", ConnectionMode.GLOBAL, "2")));

        assertThat(view.isMatched()).isFalse();
        assertThat(view.externalSupplierId()).isEqualTo("2");
    }

    @Test
    @DisplayName("a missing store is treated as having no supplier for the id")
    void toleratesMissingStore() {
        RoutedSupplierView view = RoutedSupplierView.from(order("2"), null);

        assertThat(view.isMatched()).isFalse();
    }

    @Test
    @DisplayName("a labelled connection shows its label and provider type")
    void showsTheStoredLabelAndProviderType() {
        // given
        StoreSupplierConnection kosatec = connection("Kosatec-k7f3a9c2", ConnectionMode.OWN, "7");
        kosatec.setLabel("Kosatec B2B");
        Store store = storeWith(kosatec);

        // when
        RoutedSupplierView view = RoutedSupplierView.from(order("7"), store);

        // then
        assertThat(view.supplierName()).isEqualTo("Kosatec B2B");
        assertThat(view.providerType()).isEqualTo("Kosatec");
    }
}
