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
class GlobalSuppliersInventoryFilterTest {

    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;

    private InventoryItem item(String supplier, double price) {
        return new InventoryItem("5901234567890", "MFN-1", price, "PLN", 5, 1, supplier, true, true, false);
    }

    private MatchedInventory group(InventoryItem... items) {
        return new MatchedInventory(new InventoryKey("5901234567890", "MFN-1"), List.of(items), taxonomyCache, supplierRegistry);
    }

    @Test
    void keepsOnlyStoreGlobalSuppliers() {
        // given
        GlobalSuppliersInventoryFilter filter = new GlobalSuppliersInventoryFilter(List.of("AB Group"), taxonomyCache, supplierRegistry);

        // when
        MatchedInventory result = filter.apply(group(item("AB Group", 1399.0), item("Elko", 1300.0)));

        // then
        assertThat(result.getSuppliers()).containsExactly("AB Group");
    }

    @Test
    void dropsEverythingWhenNoStoreSupplierMatches() {
        // given
        GlobalSuppliersInventoryFilter filter = new GlobalSuppliersInventoryFilter(List.of("AB Group"), taxonomyCache, supplierRegistry);

        // when
        MatchedInventory result = filter.apply(group(item("Elko", 1300.0)));

        // then
        assertThat(result.getSuppliers()).isEmpty();
    }
}
