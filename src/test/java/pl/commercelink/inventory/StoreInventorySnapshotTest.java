package pl.commercelink.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.InventoryItem;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class StoreInventorySnapshotTest {

    private final SupplierRegistry supplierRegistry = mock(SupplierRegistry.class);
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private static final InventoryItem ITEM =
            new InventoryItem("5900000000002", "MFN-1", 19.99, "EUR", 7, 3, "Acme", true, false, true,
                    "ACME-5900000000002");
    private static final InventoryItem BRANDED_ITEM =
            new InventoryItem("Logitech", "ACME-5900000000002", "5900000000002", "MFN-1", "Acme",
                    19.99, "EUR", 7, 3, true, false, true);
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
    void brandSurvivesTheRoundTrip() throws Exception {
        // given
        StoreInventorySnapshot snapshot = StoreInventorySnapshot.from(inventoryOf(BRANDED_ITEM));

        // when
        StoreInventorySnapshot back = objectMapper.readValue(objectMapper.writeValueAsString(snapshot), StoreInventorySnapshot.class);

        // then
        InventoryItem restored = back.toStoreInventory(supplierRegistry).items().iterator().next()
                .getInventoryItems().get(0);
        assertEquals(BRANDED_ITEM, restored);
    }

    @Test
    void snapshotWrittenBeforeBrandsReadsWithoutBrand() throws Exception {
        // given
        ObjectNode json = objectMapper.valueToTree(StoreInventorySnapshot.from(inventoryOf(BRANDED_ITEM)));
        ((ObjectNode) json.at("/entries/0/items/0")).remove("brand");

        // when
        StoreInventorySnapshot back = objectMapper.treeToValue(json, StoreInventorySnapshot.class);

        // then
        InventoryItem restored = back.toStoreInventory(supplierRegistry).items().iterator().next()
                .getInventoryItems().get(0);
        assertNull(restored.brand());
        assertEquals("5900000000002", restored.ean());
    }

    private StoreInventory inventoryOf(InventoryItem item) {
        MatchedInventory matched = new MatchedInventory(new InventoryKey(KEY_EANS, KEY_CODES),
                List.of(item), supplierRegistry);
        return new StoreInventory(InventoryIndex.of(List.of(matched)), LocalDateTime.of(2026, 6, 17, 10, 0));
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
    void linkSurvivesTheSnapshot() throws Exception {
        // given
        MatchedInventory own = new MatchedInventory(new InventoryKey("8720689021180", "QP1324/20"),
                List.of(ITEM), supplierRegistry);
        InventoryIndex ownIndex = InventoryIndex.of(List.of(own));
        ownIndex.link(own, List.of("8720689010825"));
        StoreInventorySnapshot snapshot =
                StoreInventorySnapshot.from(new StoreInventory(ownIndex, LocalDateTime.of(2026, 11, 16, 10, 0)));

        // when
        StoreInventorySnapshot back =
                objectMapper.readValue(objectMapper.writeValueAsString(snapshot), StoreInventorySnapshot.class);

        // then
        StoreInventory restored = back.toStoreInventory(supplierRegistry);
        MatchedInventory restoredOwn = restored.items().iterator().next();
        assertEquals(List.of("8720689010825"), List.copyOf(restoredOwn.getInventoryKey().getLinkedEans()));
        assertEquals(List.of(restoredOwn), restored.index().findMatching(InventoryKey.fromEan("8720689010825")));
    }

    @Test
    void snapshotOfInventoryWithoutLinksWritesNoLinkedEans() throws Exception {
        // given
        StoreInventorySnapshot snapshot = StoreInventorySnapshot.from(sampleInventory());

        // when
        String json = objectMapper.writeValueAsString(snapshot);

        // then
        assertFalse(json.contains("linkedEans"));
    }

    @Test
    void entryWithoutTheLinkedEansFieldRestoresWithoutLinks() throws Exception {
        // given
        ObjectNode json = objectMapper.valueToTree(StoreInventorySnapshot.from(sampleInventory()));
        ((ObjectNode) json.at("/entries/0")).remove("linkedEans");

        // when
        StoreInventorySnapshot back = objectMapper.treeToValue(json, StoreInventorySnapshot.class);

        // then
        assertNull(back.entries().get(0).linkedEans());
        MatchedInventory restored = back.toStoreInventory(supplierRegistry).items().iterator().next();
        assertTrue(restored.getInventoryKey().getLinkedEans().isEmpty());
        assertEquals(KEY_EANS, Set.copyOf(restored.getInventoryKey().getProductEans()));
    }
}
