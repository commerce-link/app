package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GlobalInventorySourceTest {

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

    @Test
    void selectsLargestGroupAmongMfnMatches() {
        // given
        MatchedInventory small = group(new InventoryKey("E1", "M1"), item("E1", "M1", "SupplierX", 100.0));
        MatchedInventory big = group(new InventoryKey("E2", "M1"),
                item("E2", "M1", "SupplierY", 90.0), item("E2", "M1", "SupplierZ", 95.0));
        GlobalInventorySource source = new GlobalInventorySource(GlobalInventoryIndex.of(List.of(small, big)), supplier -> true);
        InventoryKey lookupKey = InventoryKey.fromMfn("M1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactlyInAnyOrder("SupplierY", "SupplierZ");
    }

    @Test
    void prefersMfnMatchesOverEanOnlyMatches() {
        // given
        MatchedInventory eanOnly = group(new InventoryKey("E1", null), item("E1", "M-OTHER", "SupplierX", 100.0));
        MatchedInventory mfnMatch = group(new InventoryKey(null, "M1"), item("E2", "M1", "SupplierY", 90.0));
        GlobalInventorySource source = new GlobalInventorySource(GlobalInventoryIndex.of(List.of(eanOnly, mfnMatch)), supplier -> true);
        InventoryKey lookupKey = new InventoryKey("E1", "M1");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).containsExactly("SupplierY");
    }

    @Test
    void fallsBackToEanMatchesWhenNoMfnMatch() {
        // given
        MatchedInventory eanOnly = group(new InventoryKey("E1", null), item("E1", "M-OTHER", "SupplierX", 100.0));
        GlobalInventorySource source = new GlobalInventorySource(GlobalInventoryIndex.of(List.of(eanOnly)), supplier -> true);
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
        GlobalInventorySource source = new GlobalInventorySource(GlobalInventoryIndex.of(List.of(matched)), List.of("AB Group")::contains);
        InventoryKey lookupKey = InventoryKey.fromMfn("M1");
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
        GlobalInventorySource source = new GlobalInventorySource(GlobalInventoryIndex.of(List.of(matched)), supplier -> true);
        InventoryKey lookupKey = InventoryKey.fromMfn("M-UNKNOWN");
        MatchedInventory result = accumulator(lookupKey);

        // when
        source.mergeInto(result, lookupKey);

        // then
        assertThat(result.getSuppliers()).isEmpty();
    }
}
