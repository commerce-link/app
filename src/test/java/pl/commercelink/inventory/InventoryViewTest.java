package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InventoryViewTest {

    private final TaxonomyCache taxonomyCache = mock(TaxonomyCache.class);
    private final SupplierRegistry supplierRegistry = mock(SupplierRegistry.class);

    private InventoryItem item(String ean, String supplier) {
        return new InventoryItem(ean, "AAA", 100.0, "PLN", 5, 2, supplier, true, true, false);
    }

    @Test
    void findByEanReturnsUnionOfOwnAndGlobalItemsForSameProduct() {
        // given
        MatchedInventory own = new MatchedInventory(new InventoryKey("111", "AAA"), List.of(item("111", "ActionOwn")), taxonomyCache, supplierRegistry);
        GlobalMatchedInventory global = new GlobalMatchedInventory();
        global.replace(List.of(new MatchedInventory(new InventoryKey("111", "AAA"), List.of(item("111", "Asbis")), taxonomyCache, supplierRegistry)));
        MatchedInventorySource source = new CompositeMatchedInventorySource(List.of(own), global, Set.of("Asbis"), taxonomyCache, supplierRegistry);
        InventoryView view = new InventoryView(source);

        // when
        MatchedInventory result = view.findByEan("111");

        // then
        assertThat(result.getSuppliers()).containsExactlyInAnyOrder("ActionOwn", "Asbis");
    }
}
