package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoreSuppliersInventoryFilterTest {

    private static final String STORE_ID = "store-1";

    private final StoresRepository storesRepository = mock(StoresRepository.class);
    private final StoreInventoryProvider storeInventoryProvider = mock(StoreInventoryProvider.class);
    private final TaxonomyCache taxonomyCache = mock(TaxonomyCache.class);
    private final SupplierRegistry supplierRegistry = mock(SupplierRegistry.class);

    private InventoryItem item(String supplier, double price) {
        return new InventoryItem("5901234567890", "MFN-1", price, "PLN", 5, 1, supplier, true, true, false);
    }

    private MatchedInventory group(InventoryItem... items) {
        return new MatchedInventory(new InventoryKey("5901234567890", "MFN-1"), List.of(items), taxonomyCache, supplierRegistry);
    }

    private StoreSuppliersInventoryFilter filterFor(List<String> globalSupplierNames, List<MatchedInventory> ownGroups) {
        Store store = mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(globalSupplierNames);
        when(store.hasOwnSupplierConnections()).thenReturn(!ownGroups.isEmpty());
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.get(STORE_ID)).thenReturn(new StoreInventory(ownGroups, LocalDateTime.now()));
        return new StoreSuppliersInventoryFilter(STORE_ID, storesRepository, storeInventoryProvider, taxonomyCache, supplierRegistry);
    }

    private double priceOf(MatchedInventory matched, String supplier) {
        return matched.getInventoryItems().stream()
                .filter(i -> i.supplier().equals(supplier))
                .mapToDouble(InventoryItem::netPrice)
                .findFirst().orElse(-1);
    }

    @Test
    void keepsOnlyStoreGlobalSuppliersFromGlobalGroup() {
        // given
        StoreSuppliersInventoryFilter filter = filterFor(List.of("AB Group"), List.of());

        // when
        MatchedInventory result = filter.apply(group(item("AB Group", 1399.0), item("Elko", 1300.0)));

        // then
        assertThat(result.getSuppliers()).containsExactly("AB Group");
    }

    @Test
    void appendsOwnItemsAndExcludesGlobalOfferOfOwnSupplier() {
        // given
        StoreSuppliersInventoryFilter filter = filterFor(List.of("AB Group"), List.of(group(item("Action", 1380.0))));

        // when
        MatchedInventory result = filter.apply(group(item("Action", 1450.0), item("AB Group", 1399.0)));

        // then
        assertThat(result.getSuppliers()).containsExactlyInAnyOrder("AB Group", "Action");
        assertThat(result.getInventoryItems()).hasSize(2);
        assertThat(priceOf(result, "Action")).isEqualTo(1380.0);
        assertThat(priceOf(result, "AB Group")).isEqualTo(1399.0);
    }

    @Test
    void dropsEverythingWhenNoStoreSupplierMatches() {
        // given
        StoreSuppliersInventoryFilter filter = filterFor(List.of("AB Group"), List.of());

        // when
        MatchedInventory result = filter.apply(group(item("Elko", 1300.0)));

        // then
        assertThat(result.getSuppliers()).isEmpty();
    }
}
