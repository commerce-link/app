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
    void streamsGlobalOnlyWhenStoreHasNoOwnInventory() {
        // given
        MatchedInventory global = group(new InventoryKey("5901234567890", "MFN-1"));
        ListingInventory listing = new ListingInventory(List.of(global), List.of());

        // when / then
        assertThat(listing.stream()).containsExactly(global);
    }

    @Test
    void appendsOwnGroupsAbsentFromGlobalAndSkipsOwnGroupsAlreadyInGlobal() {
        // given
        MatchedInventory global = group(new InventoryKey("5901234567890", "MFN-1"));
        MatchedInventory ownSameProduct = group(new InventoryKey("5901234567890", "MFN-1"));
        MatchedInventory ownOnly = group(new InventoryKey("4000000000009", "MFN-OWN"));
        ListingInventory listing = new ListingInventory(List.of(global), List.of(ownSameProduct, ownOnly));

        // when / then
        assertThat(listing.stream()).containsExactly(global, ownOnly);
    }
}
