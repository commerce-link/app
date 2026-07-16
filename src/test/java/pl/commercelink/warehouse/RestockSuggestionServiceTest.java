package pl.commercelink.warehouse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.Warehouse;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestockSuggestionServiceTest {

    @Mock
    private Inventory inventory;
    @Mock
    private StockLevels stockLevels;
    @Mock
    private ProductCatalogRepository productCatalogRepository;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private Warehouse warehouse;
    @Mock
    private StockQueryService stockQueryService;

    @InjectMocks
    private RestockSuggestionService service;

    @Mock
    private Store store;
    @Mock
    private InventoryView enabledInventory;
    @Mock
    private ProductCatalog catalog;
    @Mock
    private MatchedInventory matched;

    @Test
    void sortsDeliverySuggestionsByCategoryThenName() {
        // given
        StockProductLevel gpu = new StockProductLevel("GPU", "MFN-GPU", "Zotac RTX", 1_000_000, 0, 3);
        StockProductLevel cpuAmd = new StockProductLevel("CPU", "MFN-CPU-AMD", "AMD Ryzen", 1_000_000, 0, 3);
        StockProductLevel cpuIntel = new StockProductLevel("CPU", "MFN-CPU-INTEL", "Intel Core", 1_000_000, 0, 3);

        InventoryItem offer = new InventoryItem("EAN", "MFN", 100.0, "PLN", 5, 1, "Action");

        when(storesRepository.findById("store")).thenReturn(store);
        when(store.isEnabledSupplier("Action")).thenReturn(true);
        when(inventory.withEnabledSuppliersOnly("store", SupplierScope.FULFILMENT)).thenReturn(enabledInventory);
        when(productCatalogRepository.findAll("store")).thenReturn(List.of(catalog));
        when(catalog.getCatalogId()).thenReturn("cat");
        when(stockLevels.calculate("store", "cat", null, RestockScope.WholeCatalog, false))
                .thenReturn(List.of(gpu, cpuAmd, cpuIntel));
        when(enabledInventory.findByProductCode(anyString())).thenReturn(matched);
        when(matched.isEmpty()).thenReturn(false);
        when(matched.getInventoryItemsFromSupplier("Action")).thenReturn(List.of(offer));
        when(warehouse.stockQueryService("store")).thenReturn(stockQueryService);
        when(stockQueryService.searchByMfns(eq("store"), any())).thenReturn(List.of());

        // when
        List<RestockSuggestion> result = service.suggestForDelivery("store", "Action", Collections.emptySet());

        // then
        assertThat(result).hasSize(3);
        assertThat(result).extracting(RestockSuggestion::getCategory)
                .containsExactly("CPU", "CPU", "GPU");
        assertThat(result).extracting(RestockSuggestion::getName)
                .containsExactly("AMD Ryzen", "Intel Core", "Zotac RTX");
        assertThat(result).extracting(RestockSuggestion::getPriceCategory)
                .containsOnly(RestockPriceCategory.GoodDeal);
    }

    @Test
    void sortsDeliverySuggestionsByPriceCategoryAttractiveness() {
        // given
        StockProductLevel goodDeal = new StockProductLevel("CPU", "MFN-1", "AAA", 1_000_000, 0, 3);
        StockProductLevel hotDeal = new StockProductLevel("CPU", "MFN-2", "ZZZ", 1_000_000, 0, 3);
        hotDeal.setRestockPriceHotDeal(1_000_000);

        InventoryItem offer = new InventoryItem("EAN", "MFN", 100.0, "PLN", 5, 1, "Action");

        when(storesRepository.findById("store")).thenReturn(store);
        when(store.isEnabledSupplier("Action")).thenReturn(true);
        when(inventory.withEnabledSuppliersOnly("store", SupplierScope.FULFILMENT)).thenReturn(enabledInventory);
        when(productCatalogRepository.findAll("store")).thenReturn(List.of(catalog));
        when(catalog.getCatalogId()).thenReturn("cat");
        when(stockLevels.calculate("store", "cat", null, RestockScope.WholeCatalog, false))
                .thenReturn(List.of(goodDeal, hotDeal));
        when(enabledInventory.findByProductCode(anyString())).thenReturn(matched);
        when(matched.isEmpty()).thenReturn(false);
        when(matched.getInventoryItemsFromSupplier("Action")).thenReturn(List.of(offer));
        when(warehouse.stockQueryService("store")).thenReturn(stockQueryService);
        when(stockQueryService.searchByMfns(eq("store"), any())).thenReturn(List.of());

        // when
        List<RestockSuggestion> result = service.suggestForDelivery("store", "Action", Collections.emptySet());

        // then
        assertThat(result).extracting(RestockSuggestion::getPriceCategory)
                .containsExactly(RestockPriceCategory.HotDeal, RestockPriceCategory.GoodDeal);
        assertThat(result).extracting(RestockSuggestion::getName)
                .containsExactly("ZZZ", "AAA");
    }

    @Test
    void skipsOffersWithoutHistoricalThresholds() {
        // given
        StockProductLevel level = new StockProductLevel("CPU", "MFN-1", "AMD Ryzen", 0, 0, 3);
        InventoryItem offer = new InventoryItem("EAN", "MFN", 100.0, "PLN", 5, 1, "Action");

        when(storesRepository.findById("store")).thenReturn(store);
        when(store.isEnabledSupplier("Action")).thenReturn(true);
        when(inventory.withEnabledSuppliersOnly("store", SupplierScope.FULFILMENT)).thenReturn(enabledInventory);
        when(productCatalogRepository.findAll("store")).thenReturn(List.of(catalog));
        when(catalog.getCatalogId()).thenReturn("cat");
        when(stockLevels.calculate("store", "cat", null, RestockScope.WholeCatalog, false))
                .thenReturn(List.of(level));
        when(enabledInventory.findByProductCode(anyString())).thenReturn(matched);
        when(matched.isEmpty()).thenReturn(false);
        when(matched.getInventoryItemsFromSupplier("Action")).thenReturn(List.of(offer));

        // when
        List<RestockSuggestion> result = service.suggestForDelivery("store", "Action", Collections.emptySet());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void restockWithBudgetKeepsOnlyOffersWithinThreshold() {
        // given
        StockProductLevel affordable = new StockProductLevel("CPU", "MFN-1", "AMD Ryzen", 200, 0, 3);
        StockProductLevel tooExpensive = new StockProductLevel("CPU", "MFN-2", "Intel Core", 100, 0, 3);

        InventoryItem offer = new InventoryItem("EAN", "MFN", 100.0, "PLN", 5, 1, "Action");

        when(inventory.withEnabledSuppliersOnly("store", SupplierScope.FULFILMENT)).thenReturn(enabledInventory);
        when(stockLevels.calculate("store", "cat", null, RestockScope.WholeCatalog, false))
                .thenReturn(List.of(affordable, tooExpensive));
        when(enabledInventory.findByProductCode(anyString())).thenReturn(matched);
        when(matched.isEmpty()).thenReturn(false);
        when(matched.getInventoryItems()).thenReturn(List.of(offer));

        // when
        List<RestockSuggestion> result = service.suggestForRestock("store", "cat", null, RestockScope.WholeCatalog, false, RestockPriceCategory.GoodDeal);

        // then
        assertThat(result).extracting(RestockSuggestion::getName).containsExactly("AMD Ryzen");
    }

    @Test
    void restockWithoutBudgetIncludesItemsWithoutOffers() {
        // given
        StockProductLevel level = new StockProductLevel("CPU", "MFN-1", "AMD Ryzen", 0, 0, 3);

        when(inventory.withEnabledSuppliersOnly("store", SupplierScope.FULFILMENT)).thenReturn(enabledInventory);
        when(stockLevels.calculate("store", "cat", null, RestockScope.WholeCatalog, false))
                .thenReturn(List.of(level));
        when(enabledInventory.findByProductCode(anyString())).thenReturn(null);

        // when
        List<RestockSuggestion> result = service.suggestForRestock("store", "cat", null, RestockScope.WholeCatalog, false, null);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).hasOffer()).isFalse();
        assertThat(result.get(0).getPriceCategory()).isNull();
    }
}
