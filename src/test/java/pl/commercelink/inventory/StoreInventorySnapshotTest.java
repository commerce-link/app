package pl.commercelink.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class StoreInventorySnapshotTest {

    private final TaxonomyCache taxonomyCache = mock(TaxonomyCache.class);
    private final SupplierRegistry supplierRegistry = mock(SupplierRegistry.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private StoreInventory sampleInventory() {
        InventoryItem item = new InventoryItem("5900000000002", "MFN-1", 10.0, "PLN", 5, 1, "Acme", true, true, false);
        MatchedInventory matched = new MatchedInventory(new InventoryKey("5900000000002", "MFN-1"),
                List.of(item), taxonomyCache, supplierRegistry);
        return new StoreInventory(List.of(matched), LocalDateTime.of(2026, 6, 17, 10, 0));
    }

    @Test
    void roundTripPreservesKeysAndItems() {
        StoreInventory original = sampleInventory();

        StoreInventory restored = StoreInventorySnapshot.from(original).toStoreInventory(taxonomyCache, supplierRegistry);

        assertEquals(1, restored.items().size());
        MatchedInventory m = restored.items().iterator().next();
        assertEquals(List.of("MFN-1"), List.copyOf(m.getInventoryKey().getProductCodes()));
        assertEquals(1, m.getInventoryItems().size());
        assertEquals("5900000000002", m.getInventoryItems().get(0).ean());
        assertEquals(original.builtAt(), restored.builtAt());
    }

    @Test
    void jsonRoundTripThroughObjectMapper() throws Exception {
        StoreInventorySnapshot snapshot = StoreInventorySnapshot.from(sampleInventory());

        String json = objectMapper.writeValueAsString(snapshot);
        StoreInventorySnapshot back = objectMapper.readValue(json, StoreInventorySnapshot.class);

        StoreInventory restored = back.toStoreInventory(taxonomyCache, supplierRegistry);
        MatchedInventory m = restored.items().iterator().next();
        assertEquals("5900000000002", m.getInventoryItems().get(0).ean());
        assertEquals(LocalDateTime.of(2026, 6, 17, 10, 0), restored.builtAt());
    }
}
