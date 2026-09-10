package pl.commercelink.orders.fulfilment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.Order;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalSupplierBindingTest {

    private static Store storeWith(StoreSupplierConnection... connections) {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(new ArrayList<>(List.of(connections)));
        Store store = new Store();
        store.setStoreId("store-1");
        store.setFulfilmentConfiguration(config);
        return store;
    }

    private static StoreSupplierConnection connection(String supplierName, String externalSupplierId) {
        StoreSupplierConnection connection = new StoreSupplierConnection(supplierName, ConnectionMode.GLOBAL);
        connection.setExternalSupplierId(externalSupplierId);
        return connection;
    }

    private static Order order(String orderId, String externalSupplierId) {
        Order order = new Order("store-1");
        order.setOrderId(orderId);
        order.setExternalSupplierId(externalSupplierId);
        return order;
    }

    private static FulfilmentItem candidate(String orderId, String provider) {
        FulfilmentSource source = new FulfilmentSource();
        source.setProvider(provider);
        FulfilmentAllocation allocation = new FulfilmentAllocation();
        allocation.setOrderId(orderId);
        allocation.setOrderItemId(orderId + "-item");
        return new FulfilmentItem(allocation, source, false);
    }

    @Test
    @DisplayName("an order the marketplace did not route lets every candidate through")
    void unboundOrderAcceptsEveryCandidate() {
        ExternalSupplierBinding binding = ExternalSupplierBinding.of(
                storeWith(connection("Acme", "2")), List.of(order("order-1", null)));

        assertThat(binding.test(candidate("order-1", "Acme"))).isTrue();
        assertThat(binding.test(candidate("order-1", "Bravo"))).isTrue();
        assertThat(binding.test(candidate("order-1", SupplierRegistry.WAREHOUSE))).isTrue();
    }

    @Test
    @DisplayName("a routed order keeps only the supplier configured with the same external id")
    void boundOrderKeepsOnlyTheMatchingSupplier() {
        ExternalSupplierBinding binding = ExternalSupplierBinding.of(
                storeWith(connection("Acme", "2"), connection("Bravo", "7"), connection("Charlie", null)),
                List.of(order("order-1", "2")));

        assertThat(binding.test(candidate("order-1", "Acme"))).isTrue();
        assertThat(binding.test(candidate("order-1", "acme"))).isTrue();
        assertThat(binding.test(candidate("order-1", "Bravo"))).isFalse();
        assertThat(binding.test(candidate("order-1", "Charlie"))).isFalse();
        assertThat(binding.test(candidate("order-1", SupplierRegistry.WAREHOUSE))).isFalse();
    }

    @Test
    @DisplayName("a routed order with no supplier configured for its id gets no candidate at all")
    void boundOrderWithoutConfiguredSupplierGetsNothing() {
        ExternalSupplierBinding binding = ExternalSupplierBinding.of(
                storeWith(connection("Acme", "2")), List.of(order("order-1", "9")));

        assertThat(binding.test(candidate("order-1", "Acme"))).isFalse();
    }

    @Test
    @DisplayName("the binding is decided per order when several orders are fulfilled together")
    void bindingIsDecidedPerOrder() {
        ExternalSupplierBinding binding = ExternalSupplierBinding.of(
                storeWith(connection("Acme", "2")),
                List.of(order("order-1", "2"), order("order-2", null)));

        assertThat(binding.test(candidate("order-1", "Bravo"))).isFalse();
        assertThat(binding.test(candidate("order-2", "Bravo"))).isTrue();
    }

    @Test
    @DisplayName("a missing store means no supplier can match a routed order")
    void missingStoreMatchesNothingForBoundOrders() {
        ExternalSupplierBinding binding = ExternalSupplierBinding.of(null, List.of(order("order-1", "2")));

        assertThat(binding.test(candidate("order-1", "Acme"))).isFalse();
    }

    @Test
    @DisplayName("permits answers the same question as test(), by order id and supplier name")
    void permitsMirrorsTheCandidateFilter() {
        // given
        ExternalSupplierBinding binding = ExternalSupplierBinding.of(
                storeWith(connection("Acme", "2")), List.of(order("order-1", "2"), order("order-2", null)));

        // when / then
        assertThat(binding.permits("order-1", "Acme")).isTrue();
        assertThat(binding.permits("order-1", "ACME")).isTrue();
        assertThat(binding.permits("order-1", "Bravo")).isFalse();
        assertThat(binding.permits("order-1", SupplierRegistry.WAREHOUSE)).isFalse();
        assertThat(binding.permits("order-2", "Bravo")).isTrue();
    }

    @Test
    @DisplayName("an order the binding was not built for is treated as unrouted")
    void unknownOrderIsPermittedEverywhere() {
        // given
        ExternalSupplierBinding binding = ExternalSupplierBinding.of(storeWith(connection("Acme", "2")), List.of());

        // when / then
        assertThat(binding.permits("order-9", "Bravo")).isTrue();
    }
}
