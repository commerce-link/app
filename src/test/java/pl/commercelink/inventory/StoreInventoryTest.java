package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class StoreInventoryTest {

    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;

    @Test
    void itemsDelegateToIndexContents() {
        // given
        MatchedInventory group = new MatchedInventory(new InventoryKey("E1", "M1"), List.of(), taxonomyCache, supplierRegistry);
        StoreInventory inventory = new StoreInventory(InventoryIndex.of(List.of(group)), LocalDateTime.now());

        // when
        var items = inventory.items();

        // then
        assertThat(items).containsExactly(group);
    }

    @Test
    void exposesIndexForLookup() {
        // given
        MatchedInventory group = new MatchedInventory(new InventoryKey("E1", "M1"), List.of(), taxonomyCache, supplierRegistry);
        StoreInventory inventory = new StoreInventory(InventoryIndex.of(List.of(group)), LocalDateTime.now());

        // when
        var found = inventory.index().findMatching(InventoryKey.fromMfn("M1"));

        // then
        assertThat(found).containsExactly(group);
    }
}
