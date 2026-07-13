package pl.commercelink.demo;

import org.junit.jupiter.api.Test;
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
}
