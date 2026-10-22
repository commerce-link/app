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

    // store entries in these tests carry an "-s-" id; every other id is a global PIM entry
    private static final java.util.function.Predicate<String> STORE_ENTRY = id -> id.contains("-s-");

    private MatchedInventory group(InventoryKey key) {
        return new MatchedInventory(key, taxonomyCache, supplierRegistry);
    }

    @Test
    void streamsGlobalKeysOnlyWhenStoreHasNoOwnInventory() {
        // given
        InventoryKey globalKey = new InventoryKey("5901234567890", "MFN-1");
        ListingInventory listing = new ListingInventory(InventoryIndex.of(List.of(group(globalKey))), InventoryIndex.of(List.of()), STORE_ENTRY);

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
                InventoryIndex.of(List.of(group(ownSameProductKey), group(ownOnlyKey))), STORE_ENTRY);

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
                InventoryIndex.of(List.of(group(ownKey))), STORE_ENTRY);

        // when / then
        assertThat(listing.keys()).containsExactly(globalKey, ownKey);
    }

    @Test
    void ownKeyWithTheGlobalPimIdIsStillDeduplicated() {
        // given
        InventoryKey globalKey = pimKey("g1", "5900000000031", "G1");
        InventoryKey ownKey = pimKey("g1", "5900000000031", "G1");
        ListingInventory listing = new ListingInventory(InventoryIndex.of(List.of(group(globalKey))),
                InventoryIndex.of(List.of(group(ownKey))), STORE_ENTRY);

        // when / then
        assertThat(listing.keys()).containsExactly(globalKey);
    }

    // a global group split by price loses its PIM id and keeps only codes; an own feed carrying the same global
    // product keeps the id, and must still be deduplicated by code as before (spec 4.3: only store entries are
    // matched by id alone)
    @Test
    void ownKeyWithAGlobalPimIdIsDeduplicatedByCodeWhenTheGlobalGroupLostItsId() {
        // given
        InventoryKey splitGlobalKey = new InventoryKey("5900000000041", "G4");
        InventoryKey ownKey = pimKey("g4", "5900000000041", "G4");
        ListingInventory listing = new ListingInventory(InventoryIndex.of(List.of(group(splitGlobalKey))),
                InventoryIndex.of(List.of(group(ownKey))), STORE_ENTRY);

        // when / then
        assertThat(listing.keys()).containsExactly(splitGlobalKey);
    }

    // a store entry exists because the global PIM lacks the product, while a global supplier still sells it: the
    // global group has no PIM id, and assembling it picks up the store entry's id, so it is the same listed product
    @Test
    void ownKeyOfAStoreEntryIsCoveredByAGlobalGroupWithoutIdSharingItsCodes() {
        // given
        InventoryKey globalKey = new InventoryKey("5900000000049", "E3-DUP");
        InventoryKey ownKey = pimKey("st-s-1", "5900000000049", "E3-DUP");
        ListingInventory listing = new ListingInventory(InventoryIndex.of(List.of(group(globalKey))),
                InventoryIndex.of(List.of(group(ownKey))), STORE_ENTRY);

        // when / then
        assertThat(listing.keys()).containsExactly(globalKey);
    }

    @Test
    void ownKeyOfAStoreEntryIsListedWhenAGlobalGroupWithAnotherIdAndOneWithoutIdShareNothingWithIt() {
        // given
        InventoryKey globalKey = pimKey("e3-g-c", "5900000000031", "E3-C-G");
        InventoryKey unrelatedGlobalKey = new InventoryKey("5900000000077", "OTHER");
        InventoryKey ownKey = pimKey("e3-s-c", "5900000000032", "E3-C-S");
        ListingInventory listing = new ListingInventory(InventoryIndex.of(List.of(group(globalKey), group(unrelatedGlobalKey))),
                InventoryIndex.of(List.of(group(ownKey))), STORE_ENTRY);

        // when / then
        assertThat(listing.keys()).containsExactly(globalKey, unrelatedGlobalKey, ownKey);
    }
}
