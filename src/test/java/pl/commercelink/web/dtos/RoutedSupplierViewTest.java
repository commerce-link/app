package pl.commercelink.web.dtos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.ShippingCostPolicy;
import pl.commercelink.inventory.supplier.api.ShippingPolicy;
import pl.commercelink.inventory.supplier.api.ShippingTerms;
import pl.commercelink.inventory.supplier.api.SupplierInfo;
import pl.commercelink.inventory.supplier.api.SupplierType;
import pl.commercelink.orders.Order;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoutedSupplierViewTest {

    @Mock
    private SupplierRegistry supplierRegistry;

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

    private static SupplierInfo info(String name) {
        return new SupplierInfo(name, SupplierType.Distributor, 1, "PL",
                new ShippingPolicy(new ShippingTerms(1, new ShippingCostPolicy.Free())));
    }

    @Test
    @DisplayName("an order the marketplace did not route has no routed supplier to show")
    void nothingToShowForUnroutedOrder() {
        assertThat(RoutedSupplierView.from(order(null), storeWith(), supplierRegistry)).isNull();
    }

    @Test
    @DisplayName("a routed order shows the supplier connection carrying its id")
    void showsTheMatchingSupplier() {
        when(supplierRegistry.get("Acme")).thenReturn(info("Acme"));
        Store store = storeWith(connection("Acme", ConnectionMode.GLOBAL, "2"), connection("Bravo", ConnectionMode.OWN, "7"));

        RoutedSupplierView view = RoutedSupplierView.from(order("2"), store, supplierRegistry);

        assertThat(view.isMatched()).isTrue();
        assertThat(view.supplierName()).isEqualTo("Acme");
        assertThat(view.modeKey()).isEqualTo("inventory.provider.global");
        assertThat(view.type()).isEqualTo("Distributor");
        assertThat(view.origin()).isEqualTo("PL");
        assertThat(view.enabled()).isTrue();
        assertThat(view.includeInFulfilment()).isTrue();
    }

    @Test
    @DisplayName("a routed order with no supplier configured for its id still shows the id")
    void showsTheIdWhenNoSupplierMatches() {
        RoutedSupplierView view = RoutedSupplierView.from(order("9"), storeWith(connection("Acme", ConnectionMode.GLOBAL, "2")), supplierRegistry);

        assertThat(view.isMatched()).isFalse();
        assertThat(view.externalSupplierId()).isEqualTo("9");
        assertThat(view.supplierName()).isNull();
    }

    @Test
    @DisplayName("a missing store is treated as having no supplier for the id")
    void toleratesMissingStore() {
        RoutedSupplierView view = RoutedSupplierView.from(order("2"), null, supplierRegistry);

        assertThat(view.isMatched()).isFalse();
    }
}
