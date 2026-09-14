package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.warehouse.api.Warehouse;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryStoreStatisticsTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private Warehouse warehouse;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private InventoryAutoDiscovery autoDiscovery;
    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private StoreInventoryProvider storeInventoryProvider;
    @Mock
    private GlobalMatchedInventory globalInventory;

    @InjectMocks
    private Inventory inventory;

    private Store store;

    private void storeWithElkoOffer(long globalVersion) {
        store = mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("Elko"));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownInventory(store))
                .thenReturn(new StoreInventory(InventoryIndex.of(List.of()), LocalDateTime.of(2026, 9, 14, 10, 0)));
        MatchedInventory group = new MatchedInventory(new InventoryKey("5900000000001", "A"),
                List.of(new InventoryItem("5900000000001", "A", 100.0, "PLN", 5, 1, "Elko")), taxonomyCache, supplierRegistry);
        when(globalInventory.index()).thenReturn(InventoryIndex.of(List.of(group)));
        when(globalInventory.version()).thenReturn(globalVersion);
    }

    @Test
    void storeStatisticsCountsTheStoresEnabledSuppliers() {
        // given
        storeWithElkoOffer(1);

        // when
        InventoryStatistics stats = inventory.storeStatistics(STORE_ID);

        // then
        assertThat(stats.distinctProducts()).isEqualTo(1);
        assertThat(stats.productsOf("Elko")).isEqualTo(1);
    }

    @Test
    void repeatedCallWithUnchangedIndexesReusesTheResult() {
        // given
        storeWithElkoOffer(1);

        // when
        inventory.storeStatistics(STORE_ID);
        inventory.storeStatistics(STORE_ID);

        // then
        verify(globalInventory, times(1)).index();
    }

    @Test
    void reloadedGlobalInventoryIsRecounted() {
        // given
        storeWithElkoOffer(1);
        inventory.storeStatistics(STORE_ID);
        when(globalInventory.version()).thenReturn(2L);

        // when
        inventory.storeStatistics(STORE_ID);

        // then
        verify(globalInventory, times(2)).index();
    }

    @Test
    void unknownStoreGivesEmptyStatistics() {
        // given
        when(storesRepository.findById("missing")).thenReturn(null);

        // when
        InventoryStatistics stats = inventory.storeStatistics("missing");

        // then
        assertThat(stats).isEqualTo(InventoryStatistics.EMPTY);
    }
}
