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
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryScopedSuppliersTest {

    private static final String STORE_ID = "store-1";
    private static final String EAN = "5901234567890";
    private static final String MFN = "MFN-1";

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

    private Store storeWith(StoreSupplierConnection... connections) {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(java.util.List.of(connections));
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setFulfilmentConfiguration(config);
        return store;
    }

    private InventoryItem item(String supplier, double price) {
        return new InventoryItem(EAN, MFN, price, "PLN", 5, 1, supplier, true, true, false);
    }

    @Test
    void pricingScopeExcludesGlobalSupplierWithPricingFlagOff() {
        // given
        Store store = storeWith(
                new StoreSupplierConnection("AB Group", ConnectionMode.GLOBAL, true, false),
                new StoreSupplierConnection("Elko", ConnectionMode.GLOBAL, false, true));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of()));
        MatchedInventory globalGroup = new MatchedInventory(new InventoryKey(EAN, MFN),
                List.of(item("AB Group", 1399.0), item("Elko", 1300.0)), taxonomyCache, supplierRegistry);
        when(globalInventory.index()).thenReturn(InventoryIndex.of(List.of(globalGroup)));

        // when
        MatchedInventory result = inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.PRICING).findByProductCode(MFN);

        // then
        assertThat(result.getSuppliers()).containsExactly("AB Group");
        assertThat(result.getInventoryItemsFromSupplier("Elko")).isEmpty();
        assertThat(result.getLowestPrice().netValue()).isEqualTo(1399.0);
    }

    @Test
    void fulfilmentScopeExcludesGlobalSupplierWithFulfilmentFlagOff() {
        // given
        Store store = storeWith(
                new StoreSupplierConnection("AB Group", ConnectionMode.GLOBAL, true, false),
                new StoreSupplierConnection("Elko", ConnectionMode.GLOBAL, false, true));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of()));
        MatchedInventory globalGroup = new MatchedInventory(new InventoryKey(EAN, MFN),
                List.of(item("AB Group", 1399.0), item("Elko", 1300.0)), taxonomyCache, supplierRegistry);
        when(globalInventory.index()).thenReturn(InventoryIndex.of(List.of(globalGroup)));

        // when
        MatchedInventory result = inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.FULFILMENT).findByProductCode(MFN);

        // then
        assertThat(result.getSuppliers()).containsExactly("Elko");
    }

    @Test
    void pricingScopeFiltersOwnSupplierByFlag() {
        // given
        Store store = storeWith(
                new StoreSupplierConnection("Action", ConnectionMode.OWN, true, false));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        MatchedInventory ownGroup = new MatchedInventory(new InventoryKey(EAN, MFN),
                List.of(item("Action", 1380.0)), taxonomyCache, supplierRegistry);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of(ownGroup)));
        when(globalInventory.index()).thenReturn(InventoryIndex.of(List.of()));

        // when
        MatchedInventory pricingResult = inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.PRICING).findByProductCode(MFN);
        MatchedInventory fulfilmentResult = inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.FULFILMENT).findByProductCode(MFN);

        // then
        assertThat(pricingResult.getSuppliers()).containsExactly("Action");
        assertThat(fulfilmentResult.getSuppliers()).isEmpty();
    }

    @Test
    void manualScopeFiltersBySupplierFlag() {
        // given
        Store store = storeWith(
                new StoreSupplierConnection("manual:Hurtownia A", ConnectionMode.MANUAL, true, false));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        MatchedInventory ownGroup = new MatchedInventory(new InventoryKey(EAN, MFN),
                List.of(item("manual:Hurtownia A", 1380.0)), taxonomyCache, supplierRegistry);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of(ownGroup)));
        when(globalInventory.index()).thenReturn(InventoryIndex.of(List.of()));

        // when
        MatchedInventory pricingResult = inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.PRICING).findByProductCode(MFN);
        MatchedInventory fulfilmentResult = inventory.withEnabledSuppliersOnly(STORE_ID, SupplierScope.FULFILMENT).findByProductCode(MFN);

        // then
        assertThat(pricingResult.getSuppliers()).containsExactly("manual:Hurtownia A");
        assertThat(fulfilmentResult.getSuppliers()).isEmpty();
    }

    @Test
    void fulfilmentScopeWithWarehouseAppendsWarehouseStockAndDropsExcludedSupplier() {
        // given
        Store store = storeWith(
                new StoreSupplierConnection("AB Group", ConnectionMode.GLOBAL, true, true),
                new StoreSupplierConnection("Elko", ConnectionMode.GLOBAL, true, false));
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        when(storeInventoryProvider.ownIndex(store)).thenReturn(InventoryIndex.of(List.of()));
        MatchedInventory globalGroup = new MatchedInventory(new InventoryKey(EAN, MFN),
                List.of(item("AB Group", 1399.0), item("Elko", 1300.0)), taxonomyCache, supplierRegistry);
        when(globalInventory.index()).thenReturn(InventoryIndex.of(List.of(globalGroup)));
        StockQueryService stockQueryService = mock(StockQueryService.class);
        when(warehouse.stockQueryService(STORE_ID)).thenReturn(stockQueryService);
        WarehouseItemView warehouseView = mock(WarehouseItemView.class);
        when(warehouseView.toInventoryItem()).thenReturn(new InventoryItem(EAN, MFN, 1200.0, "PLN", 3, 1, "Warehouse", true, true, false));
        when(stockQueryService.searchAvailableByMfns(eq(STORE_ID), argThat(mfns -> mfns.contains(MFN)))).thenReturn(List.of(warehouseView));

        // when
        MatchedInventory matched = inventory.withEnabledSuppliersAndWarehouseData(STORE_ID, SupplierScope.FULFILMENT).findByProductCode(MFN);

        // then
        assertThat(matched.getSuppliers()).containsExactlyInAnyOrder("AB Group", "Warehouse");
        assertThat(matched.getInventoryItemsFromSupplier("Elko")).isEmpty();
    }
}
