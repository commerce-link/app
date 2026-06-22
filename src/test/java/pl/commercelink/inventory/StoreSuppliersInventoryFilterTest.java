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
class StoreSuppliersInventoryFilterTest {

    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;

    private InventoryItem item(String supplier, double price) {
        return item("5901234567890", "MFN-1", supplier, price);
    }

    private InventoryItem item(String ean, String mfn, String supplier, double price) {
        return new InventoryItem(ean, mfn, price, "PLN", 5, 1, supplier, true, true, false);
    }

    private MatchedInventory group(InventoryItem... items) {
        return new MatchedInventory(new InventoryKey("5901234567890", "MFN-1"), List.of(items), taxonomyCache, supplierRegistry);
    }

    private double priceOf(MatchedInventory matched, String supplier) {
        return matched.getInventoryItems().stream()
                .filter(i -> i.supplier().equals(supplier))
                .mapToDouble(InventoryItem::netPrice)
                .findFirst().orElse(-1);
    }

    @Test
    void appendsOwnItemsToMatchingGroup() {
        // given
        StoreSuppliersInventoryFilter filter = new StoreSuppliersInventoryFilter(
                List.of(group(item("Action", 1380.0))), taxonomyCache, supplierRegistry);

        // when
        MatchedInventory result = filter.apply(group(item("AB Group", 1399.0)));

        // then
        assertThat(result.getSuppliers()).containsExactlyInAnyOrder("AB Group", "Action");
        assertThat(priceOf(result, "Action")).isEqualTo(1380.0);
        assertThat(priceOf(result, "AB Group")).isEqualTo(1399.0);
    }

    @Test
    void leavesGroupUnchangedWhenNoOwnInventory() {
        // given
        StoreSuppliersInventoryFilter filter = new StoreSuppliersInventoryFilter(List.of(), taxonomyCache, supplierRegistry);

        // when
        MatchedInventory result = filter.apply(group(item("AB Group", 1399.0)));

        // then
        assertThat(result.getSuppliers()).containsExactly("AB Group");
    }

    @Test
    void doesNotAppendOwnItemsForUnrelatedProduct() {
        // given
        MatchedInventory unrelatedOwn = new MatchedInventory(new InventoryKey("5909999999999", "MFN-OTHER"),
                List.of(item("5909999999999", "MFN-OTHER", "Action", 1380.0)), taxonomyCache, supplierRegistry);
        StoreSuppliersInventoryFilter filter = new StoreSuppliersInventoryFilter(List.of(unrelatedOwn), taxonomyCache, supplierRegistry);

        // when
        MatchedInventory result = filter.apply(group(item("AB Group", 1399.0)));

        // then
        assertThat(result.getSuppliers()).containsExactly("AB Group");
    }
}
