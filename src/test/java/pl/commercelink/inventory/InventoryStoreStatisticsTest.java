package pl.commercelink.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.warehouse.api.Warehouse;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    @Mock
    private PimCatalog pimCatalog;
    @Mock
    private PimView globalPimView;
    @Mock
    private PimView storePimView;

    @InjectMocks
    private Inventory inventory;

    @BeforeEach
    void globalViewOfTheCatalog() {
        when(pimCatalog.withGlobalData()).thenReturn(globalPimView);
        when(pimCatalog.withStoreData(STORE_ID)).thenReturn(storePimView);
    }

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
        verify(storeInventoryProvider, times(1)).ownInventory(store);
    }

    @Test
    void changedOwnOrManualConnectionIsRecounted() {
        // given
        storeWithElkoOffer(1);
        StoreSupplierConnection manual = mock(StoreSupplierConnection.class);
        when(manual.getSupplierName()).thenReturn("Nowak");
        when(manual.getMode()).thenReturn(ConnectionMode.MANUAL);
        when(manual.isEnabled()).thenReturn(true);
        when(store.getOwnAndManualConnections()).thenReturn(List.of(manual));
        inventory.storeStatistics(STORE_ID);

        // when
        when(manual.isEnabled()).thenReturn(false);
        inventory.storeStatistics(STORE_ID);

        // then
        verify(globalInventory, times(2)).index();
        verify(storeInventoryProvider, times(2)).ownInventory(store);
    }

    @Test
    void nullEntryInGlobalSupplierNamesIsIgnored() {
        // given
        store = mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(Arrays.asList("Elko", null));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownInventory(store))
                .thenReturn(new StoreInventory(InventoryIndex.of(List.of()), LocalDateTime.of(2026, 9, 14, 10, 0)));
        when(globalInventory.index()).thenReturn(InventoryIndex.of(List.of()));
        when(globalInventory.version()).thenReturn(1L);

        // when / then
        assertThatCode(() -> inventory.storeStatistics(STORE_ID)).doesNotThrowAnyException();
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

    // the global PIM catalog decides whether an own group's id is a store entry or a global entry. A global entry's
    // own group is deduplicated by code, as before; a store entry's own group joins only a global group of the same
    // entry or one without any (a price split, or a global supplier's row the global PIM has no entry for)
    private InventoryStatistics statisticsOfGlobalGroupAndOwnGroupWithId(String globalPimId, String ownPimId) {
        store = mock(Store.class);
        when(store.getGlobalSupplierNames()).thenReturn(List.of("Elko"));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        InventoryKey ownKey = new InventoryKey(ownPimId);
        ownKey.addEan("5900000000041");
        ownKey.addManufacturerCode("G4");
        MatchedInventory own = new MatchedInventory(ownKey,
                List.of(new InventoryItem("5900000000041", "G4", 100.0, "PLN", 3, 1, "Kosatec")), taxonomyCache, supplierRegistry);
        when(storeInventoryProvider.ownInventory(store))
                .thenReturn(new StoreInventory(InventoryIndex.of(List.of(own)), LocalDateTime.of(2026, 9, 14, 10, 0)));
        InventoryKey globalKey = new InventoryKey(globalPimId);
        MatchedInventory global = new MatchedInventory(globalKey,
                List.of(new InventoryItem("5900000000041", "G4", 100.0, "PLN", 5, 1, "Elko")), taxonomyCache, supplierRegistry);
        when(globalInventory.index()).thenReturn(InventoryIndex.of(List.of(global)));
        when(globalInventory.version()).thenReturn(1L);
        return inventory.storeStatistics(STORE_ID);
    }

    private void globalCatalogKnows(String pimId) {
        when(globalPimView.findByPimId(pimId)).thenReturn(Optional.of(new PimEntry(pimId,
                List.of(new PimIdentifier("5900000000041", PimIdentifierType.GTIN)), "B", "G4", "CPU", null, true, null, null)));
    }

    @Test
    void ownGroupOfAGlobalEntryIsCountedWithThePriceSplitGlobalGroup() {
        // given
        globalCatalogKnows("g4");

        // when
        InventoryStatistics stats = statisticsOfGlobalGroupAndOwnGroupWithId(null, "g4");

        // then
        assertThat(stats.distinctProducts()).isEqualTo(1);
    }

    @Test
    void ownGroupOfAStoreEntryIsCountedWithAGlobalGroupWithoutEntry() {
        // given
        when(globalPimView.findByPimId("s1-g4")).thenReturn(Optional.empty());

        // when
        InventoryStatistics stats = statisticsOfGlobalGroupAndOwnGroupWithId(null, "s1-g4");

        // then
        assertThat(stats.distinctProducts()).isEqualTo(1);
    }

    @Test
    void ownGroupOfAStoreEntryIsCountedApartFromAGlobalGroupOfAnotherEntry() {
        // given
        globalCatalogKnows("g4");
        when(globalPimView.findByPimId("s1-g4")).thenReturn(Optional.empty());

        // when
        InventoryStatistics stats = statisticsOfGlobalGroupAndOwnGroupWithId("g4", "s1-g4");

        // then
        assertThat(stats.distinctProducts()).isEqualTo(2);
    }
}
