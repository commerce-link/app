package pl.commercelink.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.Taxonomy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class StoreInventorySnapshotTest {

    private final SupplierRegistry supplierRegistry = mock(SupplierRegistry.class);
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private static final InventoryItem ITEM =
            new InventoryItem("5900000000002", "MFN-1", 19.99, "EUR", 7, 3, "Acme", true, false, true)
                    .withSku("ACME-5900000000002");
    private static final Set<String> KEY_EANS = Set.of("5900000000002", "4011200296908");
    private static final Set<String> KEY_CODES = Set.of("MFN-1");

    private StoreInventory sampleInventory() {
        MatchedInventory matched = new MatchedInventory(new InventoryKey(KEY_EANS, KEY_CODES),
                List.of(ITEM), supplierRegistry);
        return new StoreInventory(InventoryIndex.of(List.of(matched)), LocalDateTime.of(2026, 6, 17, 10, 0));
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
        assertEquals(expected.sku(), actual.sku());
    }

    @Test
    void roundTripPreservesKeysAndItems() {
        // given
        StoreInventory original = sampleInventory();

        // when
        StoreInventory restored = StoreInventorySnapshot.from(original).toStoreInventory(supplierRegistry);

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
        StoreInventory restored = back.toStoreInventory(supplierRegistry);
        MatchedInventory m = restored.items().iterator().next();
        assertEquals("5900000000002", m.getInventoryItems().get(0).ean());
        assertItemEquals(ITEM, m.getInventoryItems().get(0));
        assertEquals(List.of("MFN-1"), List.copyOf(m.getInventoryKey().getProductCodes()));
        assertEquals(KEY_EANS, Set.copyOf(m.getInventoryKey().getProductEans()));
        assertEquals(LocalDateTime.of(2026, 6, 17, 10, 0), restored.builtAt());
    }

    @Test
    void jsonRoundTripPreservesPimId() throws Exception {
        // given
        InventoryKey keyWithId = new InventoryKey("PIM-123");
        keyWithId.addEan("5900000000002");
        keyWithId.addManufacturerCode("MFN-1");
        MatchedInventory matched = new MatchedInventory(keyWithId, List.of(ITEM), supplierRegistry);
        StoreInventorySnapshot snapshot =
                StoreInventorySnapshot.from(new StoreInventory(InventoryIndex.of(List.of(matched)), LocalDateTime.of(2026, 6, 17, 10, 0)));

        // when
        StoreInventorySnapshot back =
                objectMapper.readValue(objectMapper.writeValueAsString(snapshot), StoreInventorySnapshot.class);

        // then
        StoreInventory restored = back.toStoreInventory(supplierRegistry);
        assertEquals("PIM-123", restored.items().iterator().next().getInventoryKey().getId());
    }

    @Test
    void jsonRoundTripCarriesProductIdentityWithTheOffering() throws Exception {
        // given
        Taxonomy taxonomy = new Taxonomy("5900000000002", "MFN-1", "Acme", "Widget Pro",
                "Keyboards", 7, 100, 200, "Peryferia > Klawiatury", "301");
        MatchedInventory matched = new MatchedInventory(new InventoryKey(KEY_EANS, KEY_CODES),
                List.of(ITEM), supplierRegistry);
        matched.adoptTaxonomy(taxonomy);
        StoreInventorySnapshot snapshot = StoreInventorySnapshot.from(
                new StoreInventory(InventoryIndex.of(List.of(matched)), LocalDateTime.of(2026, 6, 17, 10, 0)));

        // when
        StoreInventorySnapshot back =
                objectMapper.readValue(objectMapper.writeValueAsString(snapshot), StoreInventorySnapshot.class);

        // then
        Taxonomy restored = back.toStoreInventory(supplierRegistry).items().iterator().next().getTaxonomy();
        assertEquals("Widget Pro", restored.name());
        assertEquals("Acme", restored.brand());
        assertEquals("Keyboards", restored.category());
        assertEquals("301", restored.categoryId());
        assertEquals("MFN-1", restored.mfn());
        assertEquals("5900000000002", restored.ean());
        assertEquals(7, restored.dataAccuracyScore());
        assertEquals(100, restored.netWeightInGrams());
        assertEquals(200, restored.grossWeightInGrams());
        assertEquals("Peryferia > Klawiatury", restored.rawCategory());
        assertEquals(taxonomy, restored);
    }

    @Test
    void offeringWithoutProductIdentityRestoresAsEmptyTaxonomy() throws Exception {
        // given
        StoreInventorySnapshot snapshot = StoreInventorySnapshot.from(sampleInventory());

        // when
        StoreInventorySnapshot back =
                objectMapper.readValue(objectMapper.writeValueAsString(snapshot), StoreInventorySnapshot.class);

        // then
        assertEquals(Taxonomy.EMPTY, back.toStoreInventory(supplierRegistry).items().iterator().next().getTaxonomy());
    }
}
