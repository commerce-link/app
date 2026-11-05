package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.products.Product;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GroupInventorySourceTest {

    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;

    private InventoryItem item(String ean, String mfn, String supplier, double price) {
        return new InventoryItem(ean, mfn, price, "PLN", 5, 1, supplier, true, true, false);
    }

    private MatchedInventory group(InventoryKey key, InventoryItem... items) {
        return new MatchedInventory(key, List.of(items), taxonomyCache, supplierRegistry);
    }

    private MatchedInventory accumulator(InventoryKey lookupKey) {
        return new MatchedInventory(lookupKey.copy(), taxonomyCache, supplierRegistry);
    }

    private static InventoryKey productKey(String pimId, String ean, String mfn) {
        return InventoryKey.fromProduct(new Product("cat-1", pimId, ean, mfn, "Acme", null, "Product", null));
    }

    @Test
    void selectsLargestGroupAmongEanMatches() {
        // given
        MatchedInventory small = group(new InventoryKey("E1", "M1"), item("E1", "M1", "SupplierX", 100.0));
        MatchedInventory big = group(new InventoryKey("E1", "M2"),
                item("E1", "M2", "SupplierY", 90.0), item("E1", "M2", "SupplierZ", 95.0));
        GroupInventorySource source = GroupInventorySource.global(InventoryIndex.of(List.of(small, big)), supplier -> true);
        InventoryKey lookupKey = InventoryKey.fromEan("E1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactlyInAnyOrder("SupplierY", "SupplierZ");
    }

    @Test
    void eanWinsOverCode() {
        // given
        MatchedInventory eanOnly = group(new InventoryKey("E1", null), item("E1", "M-OTHER", "SupplierX", 100.0));
        MatchedInventory mfnMatch = group(new InventoryKey(null, "M1"),
                item("E2", "M1", "SupplierY", 90.0), item("E2", "M1", "SupplierZ", 95.0));
        GroupInventorySource source = GroupInventorySource.global(InventoryIndex.of(List.of(eanOnly, mfnMatch)), supplier -> true);
        InventoryKey lookupKey = new InventoryKey("E1", "M1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactly("SupplierX");
    }

    @Test
    void fallsBackToEanMatchesWhenNoMfnMatch() {
        // given
        MatchedInventory eanOnly = group(new InventoryKey("E1", null), item("E1", "M-OTHER", "SupplierX", 100.0));
        GroupInventorySource source = GroupInventorySource.global(InventoryIndex.of(List.of(eanOnly)), supplier -> true);
        InventoryKey lookupKey = InventoryKey.fromEan("E1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactly("SupplierX");
    }

    @Test
    void narrowsToConfiguredGlobalSuppliers() {
        // given
        MatchedInventory matched = group(new InventoryKey("E1", "M1"),
                item("E1", "M1", "AB Group", 1399.0), item("E1", "M1", "Elko", 1300.0));
        GroupInventorySource source = GroupInventorySource.global(InventoryIndex.of(List.of(matched)), List.of("AB Group")::contains);
        InventoryKey lookupKey = InventoryKey.fromEan("E1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactly("AB Group");
    }

    @Test
    void mergesNothingWhenNoGroupMatches() {
        // given
        MatchedInventory matched = group(new InventoryKey("E1", "M1"), item("E1", "M1", "AB Group", 1399.0));
        GroupInventorySource source = GroupInventorySource.global(InventoryIndex.of(List.of(matched)), supplier -> true);
        InventoryKey lookupKey = InventoryKey.fromEan("E-UNKNOWN");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).isEmpty();
    }

    @Test
    void appendsItemsFromOwnGroupsMatchingLookupKey() {
        // given
        MatchedInventory own = group(new InventoryKey("E1", "M1"), item("E1", "M1", "Action", 1380.0));
        GroupInventorySource source = GroupInventorySource.own(InventoryIndex.of(List.of(own)));
        InventoryKey lookupKey = InventoryKey.fromEan("E1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactly("Action");
    }

    @Test
    void ownEanMatchWinsOverCodeMatch() {
        // given
        MatchedInventory eanMatch = group(new InventoryKey("E1", null), item("E1", "M-OTHER", "SupplierX", 100.0));
        MatchedInventory mfnMatch = group(new InventoryKey(null, "M1"),
                item("E2", "M1", "SupplierY", 90.0), item("E2", "M1", "SupplierZ", 95.0));
        GroupInventorySource source = GroupInventorySource.own(InventoryIndex.of(List.of(eanMatch, mfnMatch)));
        InventoryKey lookupKey = new InventoryKey("E1", "M1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactly("SupplierX");
    }

    @Test
    void selectsLargestOwnGroupAmongEanMatches() {
        // given
        MatchedInventory small = group(new InventoryKey("E1", "M1"), item("E1", "M1", "Action", 100.0));
        MatchedInventory big = group(new InventoryKey("E1", "M2"),
                item("E1", "M2", "Wortmann", 90.0), item("E1", "M2", "Asbis", 95.0));
        GroupInventorySource source = GroupInventorySource.own(InventoryIndex.of(List.of(small, big)));
        InventoryKey lookupKey = InventoryKey.fromEan("E1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactlyInAnyOrder("Wortmann", "Asbis");
    }

    @Test
    void ignoresOwnGroupsThatDoNotMatch() {
        // given
        MatchedInventory unrelated = group(new InventoryKey("E2", "M2"), item("E2", "M2", "Action", 1380.0));
        GroupInventorySource source = GroupInventorySource.own(InventoryIndex.of(List.of(unrelated)));
        InventoryKey lookupKey = InventoryKey.fromEan("E1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).isEmpty();
    }

    @Test
    void narrowsOwnGroupToConfiguredSuppliers() {
        // given
        MatchedInventory matched = group(new InventoryKey("E1", "M1"),
                item("E1", "M1", "Action", 1380.0), item("E1", "M1", "Wortmann", 1300.0));
        GroupInventorySource source = GroupInventorySource.own(InventoryIndex.of(List.of(matched)), List.of("Action")::contains);
        InventoryKey lookupKey = InventoryKey.fromEan("E1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactly("Action");
    }

    @Test
    void mergesNothingWhenOwnInventoryEmpty() {
        // given
        GroupInventorySource source = GroupInventorySource.own(InventoryIndex.of(List.of()));
        InventoryKey lookupKey = InventoryKey.fromEan("E1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).isEmpty();
    }

    private static InventoryKey pimKey(String id, String ean, String mfn) {
        InventoryKey key = new InventoryKey(id);
        key.addEan(ean);
        key.addManufacturerCode(mfn);
        return key;
    }

    @Test
    void globalSourceSkipsAGroupOfAnotherPimEntryForAStoreEntryRow() {
        // given
        MatchedInventory otherEntry = group(pimKey("e3-g-c", "5900000000032", "E3-C-G"), item("5900000000032", "E3-C-G", "AB Group", 100.0));
        GroupInventorySource source = GroupInventorySource.global(InventoryIndex.of(List.of(otherEntry)), supplier -> true,
                id -> id.contains("-s-"));
        InventoryKey lookupKey = pimKey("e3-s-c", "5900000000032", "E3-C-S");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.getInventoryKey().getProductEans()).containsExactly("5900000000032");
    }

    @Test
    void globalSourceMergesAGroupWithoutPimIdIntoAStoreEntryRow() {
        // given
        MatchedInventory noEntry = group(new InventoryKey("5900000000049", "E3-DUP"), item("5900000000049", "E3-DUP", "AB Group", 100.0));
        GroupInventorySource source = GroupInventorySource.global(InventoryIndex.of(List.of(noEntry)), supplier -> true,
                id -> id.contains("-s-"));
        InventoryKey lookupKey = pimKey("st-s-1", "5900000000049", "E3-DUP");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactly("AB Group");
        assertThat(result.getInventoryKey().getId()).isEqualTo("st-s-1");
    }

    @Test
    void globalSourceStillMergesGroupsOfOtherPimEntriesForAGlobalEntryRow() {
        // given
        MatchedInventory otherEntry = group(pimKey("g2", "5900000000032", "G2"), item("5900000000032", "G2", "AB Group", 100.0));
        GroupInventorySource source = GroupInventorySource.global(InventoryIndex.of(List.of(otherEntry)), supplier -> true,
                id -> id.contains("-s-"));
        InventoryKey lookupKey = pimKey("g1", "5900000000032", "G1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactly("AB Group");
    }

    // a store entry "clone" cloned from the global entry "source"; other "-s-" ids are plain store entries
    private static StoreEntryKeys cloneOf(String clone, String source) {
        return new StoreEntryKeys() {
            @Override
            public boolean isStoreEntry(String pimId) {
                return pimId.contains("-s-");
            }

            @Override
            public String sourceOf(String pimId) {
                return clone.equals(pimId) ? source : null;
            }
        };
    }

    @Test
    void cloneKeyGetsTheGlobalGroupOfItsSource() {
        // given
        MatchedInventory sourceGroup = group(pimKey("g-1", "5900000000031", "SRC-1"), item("5900000000031", "SRC-1", "AB Group", 100.0));
        GroupInventorySource source = GroupInventorySource.global(InventoryIndex.of(List.of(sourceGroup)), supplier -> true,
                cloneOf("st-s-c", "g-1"));
        InventoryKey lookupKey = pimKey("st-s-c", "5900000000031", "SRC-1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactly("AB Group");
        assertThat(result.getInventoryKey().getId()).isEqualTo("st-s-c");
    }

    @Test
    void plainStoreEntryStillSkipsAnotherGlobalEntrysGroupWhenAnotherEntryIsCloned() {
        // given
        MatchedInventory otherEntry = group(pimKey("g-2", "5900000000032", "E-X"), item("5900000000032", "E-X", "AB Group", 100.0));
        GroupInventorySource source = GroupInventorySource.global(InventoryIndex.of(List.of(otherEntry)), supplier -> true,
                cloneOf("st-s-c", "g-1"));
        InventoryKey lookupKey = pimKey("st-s-plain", "5900000000032", "E-X");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void cloneWithoutSourceBehavesAsToday() {
        // given
        MatchedInventory otherEntry = group(pimKey("g-1", "5900000000032", "SRC-1"), item("5900000000032", "SRC-1", "AB Group", 100.0));
        GroupInventorySource source = GroupInventorySource.global(InventoryIndex.of(List.of(otherEntry)), supplier -> true,
                cloneOf("st-s-c", null));
        InventoryKey lookupKey = pimKey("st-s-c", "5900000000032", "SRC-1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void cloneWithChangedCodesGetsNoSourceOffers() {
        // given
        MatchedInventory sourceGroup = group(pimKey("g-1", "5900000000031", "SRC-1"), item("5900000000031", "SRC-1", "AB Group", 100.0));
        GroupInventorySource source = GroupInventorySource.global(InventoryIndex.of(List.of(sourceGroup)), supplier -> true,
                cloneOf("st-s-c", "g-1"));
        InventoryKey lookupKey = pimKey("st-s-c", "5900000000099", "CHANGED");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void pimIdWinsOverEan() {
        // given
        MatchedInventory byPimId = group(pimKey("P1", "5900000000011", "M-P"), item("5900000000011", "M-P", "SupplierP", 100.0));
        MatchedInventory byEan = group(new InventoryKey("5900000000028", "M-E"),
                item("5900000000028", "M-E", "SupplierE1", 100.0), item("5900000000028", "M-E", "SupplierE2", 100.0));
        GroupInventorySource source = GroupInventorySource.global(InventoryIndex.of(List.of(byPimId, byEan)), supplier -> true);
        InventoryKey lookupKey = productKey("P1", "5900000000028", "M-E");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactly("SupplierP");
    }

    @Test
    void productKeyFindsItsGroupByPimId() {
        // given
        MatchedInventory group = group(pimKey("P1", "5900000000011", "M-OLD"), item("5900000000011", "M-OLD", "SupplierP", 100.0));
        GroupInventorySource source = GroupInventorySource.global(InventoryIndex.of(List.of(group)), supplier -> true);
        InventoryKey lookupKey = productKey("P1", "5900000000099", "M-NEW");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactly("SupplierP");
    }

    @Test
    void productWithADeadPinStillMatchesByEan() {
        // given
        MatchedInventory group = group(new InventoryKey("5900000000011", "M1"), item("5900000000011", "M1", "SupplierE", 100.0));
        GroupInventorySource source = GroupInventorySource.global(InventoryIndex.of(List.of(group)), supplier -> true,
                id -> id.contains("-s-"));
        InventoryKey lookupKey = productKey("st-s-deleted", "5900000000011", "M1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactly("SupplierE");
    }

    @Test
    void productGetsNoOffersOfAGroupSharingOnlyItsCode() {
        // given
        MatchedInventory otherProduct = group(new InventoryKey("5900000000042", "M1"), item("5900000000042", "M1", "SupplierB", 100.0));
        GroupInventorySource source = GroupInventorySource.global(InventoryIndex.of(List.of(otherProduct)), supplier -> true);
        InventoryKey lookupKey = productKey(null, "5900000000066", "M1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.isEmpty()).isTrue();
    }
}
