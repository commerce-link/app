package pl.commercelink.demo;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.localdev.CatalogSeed;
import pl.commercelink.localdev.CatalogSeedRow;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.stores.DemoStoreMetadata;
import pl.commercelink.stores.Store;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DemoStoreSeederTest {

    @Test
    void appliesCompleteDemoConfigurationToNewStore() {
        // given
        Store store = new Store();
        DemoStoreMetadata metadata = new DemoStoreMetadata("user@example.com", "2026-07-08T10:00:00Z", "2026-07-22T10:00:00Z");

        // when
        DemoStoreSeeder.applyStoreConfiguration(store, "abc123def4", "Sklep demo", metadata);

        // then
        assertEquals("abc123def4", store.getStoreId());
        assertEquals("Sklep demo", store.getName());
        assertSame(metadata, store.getDemo());
        assertTrue(store.canUseGlobalSuppliers());
        assertEquals(List.of("Acme", "AcmeB"), store.getGlobalSupplierNames());
        assertEquals("MAG-abc123def4", store.getWarehouseConfiguration().getWarehouseId());
        assertEquals("KC-abc123def4", store.getWarehouseConfiguration().getCostCenterId());
        assertEquals(2, store.getCheckoutConfiguration().getDeliveryOptions().size());
        assertEquals(1, store.getBankAccounts().size());
        assertEquals(2, store.getShippingConfiguration().getPackageTemplates().size());
        assertNotNull(store.getRmaConfiguration().getCarrier());
    }

    @Test
    void keepsExistingStoreNameAndSkipsDemoMarkerWhenNull() {
        // given
        Store store = new Store();
        store.setName("Demo Store");

        // when
        DemoStoreSeeder.applyStoreConfiguration(store, "uma2dqukxr", "ignored", null);

        // then
        assertEquals("Demo Store", store.getName());
        assertNull(store.getDemo());
    }

    @Test
    void buildsTwoOrdersWithItemsInAllocation() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        DemoOrders demoOrders = DemoStoreSeeder.buildDemoOrders("store-1", "a@b.pl", rows);

        // then
        List<Order> allocationOrders = demoOrders.orders().stream()
                .filter(o -> o.getStatus() == OrderStatus.New)
                .filter(o -> demoOrders.itemsByOrderId().get(o.getOrderId()).stream()
                        .anyMatch(i -> i.getStatus() == FulfilmentStatus.Allocation))
                .toList();
        assertEquals(2, allocationOrders.size());
        allocationOrders.forEach(o -> {
            assertEquals("a@b.pl", o.getBillingDetails().getEmail());
            assertNotNull(o.getShippingDetails());
            assertTrue(o.getTotalPrice() > 0);
            demoOrders.itemsByOrderId().get(o.getOrderId()).forEach(i -> {
                assertTrue(i.isInAllocation());
                assertNotNull(i.getEan());
                assertNotNull(i.getManufacturerCode());
                assertNotNull(i.getDeliveryId());
                assertTrue(i.getCost() > 0);
            });
        });
    }

    @Test
    void buildsPersistedDeliveryWithOrderedItems() {
        // given
        List<CatalogSeedRow> rows = CatalogSeed.load();

        // when
        DemoOrders demoOrders = DemoStoreSeeder.buildDemoOrders("store-1", "a@b.pl", rows);

        // then
        Delivery delivery = demoOrders.delivery();
        assertNotNull(delivery.getEstimatedDeliveryAt());
        assertEquals("store-1", delivery.getStoreId());
        List<OrderItem> orderedItems = demoOrders.itemsByOrderId().values().stream()
                .flatMap(List::stream)
                .filter(i -> i.getStatus() == FulfilmentStatus.Ordered)
                .toList();
        assertFalse(orderedItems.isEmpty());
        orderedItems.forEach(i -> assertEquals(delivery.getDeliveryId(), i.getDeliveryId()));
    }
}
