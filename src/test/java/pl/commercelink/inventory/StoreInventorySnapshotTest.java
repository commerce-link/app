package pl.commercelink.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class StoreInventorySnapshotTest {

    private final TaxonomyCache taxonomyCache = mock(TaxonomyCache.class);
    private final SupplierRegistry supplierRegistry = mock(SupplierRegistry.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final InventoryItem ITEM =
            new InventoryItem("5900000000002", "MFN-1", 19.99, "EUR", 7, 3, "Acme", true, false, true);
    private static final Set<String> KEY_EANS = Set.of("5900000000002", "4011200296908");
    private static final Set<String> KEY_CODES = Set.of("MFN-1");

    private StoreInventory sampleInventory() {
        MatchedInventory matched = new MatchedInventory(new InventoryKey(KEY_EANS, KEY_CODES),
                List.of(ITEM), taxonomyCache, supplierRegistry);
        return new StoreInventory(List.of(matched), LocalDateTime.of(2026, 6, 17, 10, 0));
    }

    private static void assertItemEquals(InventoryItem expected, InventoryItem actual) {
        assertEquals(expected.ean(), actual.ean());
        assertEquals(expected.mfn(), actual.mfn());
        assertEquals(expected.netPrice(), actual.netPrice());
        assertEquals(expected.currency(), actual.currency());
        assertEquals(expected.qty(), actual.qty());
        assertEquals(expected.leadTimeDays(), actual.leadTimeDays());
        assertEquals(expected.supplier(), actual.supplier());
        assertEquals(expected.sellable(), actual.sellable());
        assertEquals(expected.inStock(), actual.inStock());
        assertEquals(expected.inDelivery(), actual.inDelivery());
    }

    @Test
    void roundTripPreservesKeysAndItems() {
        // given
        StoreInventory original = sampleInventory();

        // when
        StoreInventory restored = StoreInventorySnapshot.from(original).toStoreInventory(taxonomyCache, supplierRegistry);

        // then
        assertEquals(1, restored.items().size());
        MatchedInventory m = restored.items().iterator().next();
        assertEquals(List.of("MFN-1"), List.copyOf(m.getInventoryKey().getProductCodes()));
        assertEquals(KEY_EANS, Set.copyOf(m.getInventoryKey().getProductEans()));
        assertEquals(1, m.getInventoryItems().size());
        assertEquals("5900000000002", m.getInventoryItems().get(0).ean());
        assertItemEquals(ITEM, m.getInventoryItems().get(0));
        assertEquals(original.builtAt(), restored.builtAt());
    }

    @Test
    void jsonRoundTripThroughObjectMapper() throws Exception {
        // given
        StoreInventorySnapshot snapshot = StoreInventorySnapshot.from(sampleInventory());

        // when
        String json = objectMapper.writeValueAsString(snapshot);
        StoreInventorySnapshot back = objectMapper.readValue(json, StoreInventorySnapshot.class);

        // then
        StoreInventory restored = back.toStoreInventory(taxonomyCache, supplierRegistry);
        MatchedInventory m = restored.items().iterator().next();
        assertEquals("5900000000002", m.getInventoryItems().get(0).ean());
        assertItemEquals(ITEM, m.getInventoryItems().get(0));
        assertEquals(List.of("MFN-1"), List.copyOf(m.getInventoryKey().getProductCodes()));
        assertEquals(KEY_EANS, Set.copyOf(m.getInventoryKey().getProductEans()));
        assertEquals(LocalDateTime.of(2026, 6, 17, 10, 0), restored.builtAt());
    }
}
