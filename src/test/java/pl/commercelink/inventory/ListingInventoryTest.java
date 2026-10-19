package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ListingInventoryTest {

    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;

    private MatchedInventory group(InventoryKey key) {
        return new MatchedInventory(key, taxonomyCache, supplierRegistry);
    }

    @Test
    void streamsGlobalKeysOnlyWhenStoreHasNoOwnInventory() {
        // given
        InventoryKey globalKey = new InventoryKey("5901234567890", "MFN-1");
        ListingInventory listing = new ListingInventory(InventoryIndex.of(List.of(group(globalKey))), InventoryIndex.of(List.of()));

        // when / then
        assertThat(listing.keys()).containsExactly(globalKey);
    }

    @Test
    void appendsOwnKeysAbsentFromGlobalAndSkipsOwnKeysAlreadyInGlobal() {
        // given
        InventoryKey globalKey = new InventoryKey("5901234567890", "MFN-1");
        InventoryKey ownSameProductKey = new InventoryKey("5901234567890", "MFN-1");
        InventoryKey ownOnlyKey = new InventoryKey("4000000000009", "MFN-OWN");
        ListingInventory listing = new ListingInventory(InventoryIndex.of(List.of(group(globalKey))),
                InventoryIndex.of(List.of(group(ownSameProductKey), group(ownOnlyKey))));

        // when / then
        assertThat(listing.keys()).containsExactly(globalKey, ownOnlyKey);
    }

    private InventoryKey pimKey(String id, String ean, String code) {
        InventoryKey key = new InventoryKey(id);
        key.addEan(ean);
        key.addManufacturerCode(code);
        return key;
    }

    @Test
    void ownKeyOfAStoreEntryIsListedEvenWhenAGlobalRowSharesItsMpn() {
        // given
        InventoryKey globalKey = pimKey("e3-g-c", "5900000000031", "E3-C-S");
        InventoryKey ownKey = pimKey("e3-s-c", "5900000000032", "E3-C-S");
        ListingInventory listing = new ListingInventory(InventoryIndex.of(List.of(group(globalKey))),
                InventoryIndex.of(List.of(group(ownKey))));

        // when / then
        assertThat(listing.keys()).containsExactly(globalKey, ownKey);
    }

    @Test
    void ownKeyWithTheGlobalPimIdIsStillDeduplicated() {
        // given
        InventoryKey globalKey = pimKey("g1", "5900000000031", "G1");
        InventoryKey ownKey = pimKey("g1", "5900000000031", "G1");
        ListingInventory listing = new ListingInventory(InventoryIndex.of(List.of(group(globalKey))),
                InventoryIndex.of(List.of(group(ownKey))));

        // when / then
        assertThat(listing.keys()).containsExactly(globalKey);
    }
}
