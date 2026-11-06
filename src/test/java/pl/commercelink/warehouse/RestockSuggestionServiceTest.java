package pl.commercelink.warehouse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.invoicing.api.Price;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.warehouse.api.ItemCondition;
import pl.commercelink.warehouse.api.StockQueryService;
import pl.commercelink.warehouse.api.Warehouse;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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

    private static StockProductLevel level(String category, String mfn, String name, double promo) {
        return new StockProductLevel(category, "PIM-" + mfn, "EAN-" + mfn, mfn, name, promo, 0, 3);
    }

    @Test
    void sortsDeliverySuggestionsByCategoryThenName() {
        // given
        StockProductLevel gpu = level("GPU", "MFN-GPU", "Zotac RTX", 1_000_000);
        StockProductLevel cpuAmd = level("CPU", "MFN-CPU-AMD", "AMD Ryzen", 1_000_000);
        StockProductLevel cpuIntel = level("CPU", "MFN-CPU-INTEL", "Intel Core", 1_000_000);

        InventoryItem offer = new InventoryItem("EAN", "MFN", 100.0, "PLN", 5, 1, "Action");

        when(storesRepository.findById("store")).thenReturn(store);
        when(store.isEnabledSupplier("Action")).thenReturn(true);
        when(inventory.withEnabledSuppliersOnly("store", SupplierScope.FULFILMENT)).thenReturn(enabledInventory);
        when(productCatalogRepository.findAll("store")).thenReturn(List.of(catalog));
        when(catalog.getCatalogId()).thenReturn("cat");
        when(stockLevels.calculate("store", "cat", null, RestockScope.WholeCatalog, false, true))
                .thenReturn(List.of(gpu, cpuAmd, cpuIntel));
        when(enabledInventory.findByInventoryKey(any())).thenReturn(matched);
        when(matched.isEmpty()).thenReturn(false);
        when(matched.getInventoryKey()).thenReturn(new InventoryKey());
        when(matched.getInventoryItemsFromSupplier("Action")).thenReturn(List.of(offer));
        when(warehouse.stockQueryService("store")).thenReturn(stockQueryService);
        when(stockQueryService.searchByEans(eq("store"), any())).thenReturn(List.of());

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
        StockProductLevel goodDeal = level("CPU", "MFN-1", "AAA", 1_000_000);
        StockProductLevel hotDeal = level("CPU", "MFN-2", "ZZZ", 1_000_000);
        hotDeal.setRestockPriceHotDeal(1_000_000);

        InventoryItem offer = new InventoryItem("EAN", "MFN", 100.0, "PLN", 5, 1, "Action");

        when(storesRepository.findById("store")).thenReturn(store);
        when(store.isEnabledSupplier("Action")).thenReturn(true);
        when(inventory.withEnabledSuppliersOnly("store", SupplierScope.FULFILMENT)).thenReturn(enabledInventory);
        when(productCatalogRepository.findAll("store")).thenReturn(List.of(catalog));
        when(catalog.getCatalogId()).thenReturn("cat");
        when(stockLevels.calculate("store", "cat", null, RestockScope.WholeCatalog, false, true))
                .thenReturn(List.of(goodDeal, hotDeal));
        when(enabledInventory.findByInventoryKey(any())).thenReturn(matched);
        when(matched.isEmpty()).thenReturn(false);
        when(matched.getInventoryKey()).thenReturn(new InventoryKey());
        when(matched.getInventoryItemsFromSupplier("Action")).thenReturn(List.of(offer));
        when(warehouse.stockQueryService("store")).thenReturn(stockQueryService);
        when(stockQueryService.searchByEans(eq("store"), any())).thenReturn(List.of());

        // when
        List<RestockSuggestion> result = service.suggestForDelivery("store", "Action", Collections.emptySet());

        // then
        assertThat(result).extracting(RestockSuggestion::getPriceCategory)
                .containsExactly(RestockPriceCategory.HotDeal, RestockPriceCategory.GoodDeal);
        assertThat(result).extracting(RestockSuggestion::getName)
                .containsExactly("ZZZ", "AAA");
    }

    @Test
    void deliverySkipsOnlyTheProductInStockUnderAnEanOfItsGroup() {
        // given
        StockProductLevel asus = new StockProductLevel("GPU", "PIM-A", "5901000000011", "X1", "Asus X1", 1_000_000, 0, 3);
        StockProductLevel gigabyte = new StockProductLevel("GPU", "PIM-G", "5901000000028", "X1", "Gigabyte X1", 1_000_000, 0, 3);
        MatchedInventory asusGroup = group("PIM-A", "5901000000011", "5901000000035");
        MatchedInventory gigabyteGroup = group("PIM-G", "5901000000028");
        WarehouseItemView asusUnderSecondEan = new WarehouseItemView("store", "item-1", "Asus X1", "5901000000035", "X1",
                Price.fromNet(100.0), 1, FulfilmentStatus.Delivered, ItemCondition.Sealed);

        when(storesRepository.findById("store")).thenReturn(store);
        when(store.isEnabledSupplier("Action")).thenReturn(true);
        when(inventory.withEnabledSuppliersOnly("store", SupplierScope.FULFILMENT)).thenReturn(enabledInventory);
        when(productCatalogRepository.findAll("store")).thenReturn(List.of(catalog));
        when(catalog.getCatalogId()).thenReturn("cat");
        when(stockLevels.calculate("store", "cat", null, RestockScope.WholeCatalog, false, true))
                .thenReturn(List.of(asus, gigabyte));
        when(enabledInventory.findByInventoryKey(any()))
                .thenAnswer(call -> "PIM-A".equals(call.<InventoryKey>getArgument(0).getId()) ? asusGroup : gigabyteGroup);
        when(warehouse.stockQueryService("store")).thenReturn(stockQueryService);
        when(stockQueryService.searchByEans(eq("store"), any())).thenAnswer(call ->
                call.<Collection<String>>getArgument(1).contains(asusUnderSecondEan.getEan()) ? List.of(asusUnderSecondEan) : List.of());

        // when
        List<RestockSuggestion> result = service.suggestForDelivery("store", "Action", Collections.emptySet());

        // then
        assertThat(result).extracting(RestockSuggestion::getName).containsExactly("Gigabyte X1");
    }

    @Test
    void productPlannedInTheDeliveryUnderAnEanOfItsGroupIsNotSuggested() {
        // given
        StockProductLevel asus = new StockProductLevel("GPU", "PIM-A", "5901000000011", "X1", "Asus X1", 1_000_000, 0, 3);
        StockProductLevel gigabyte = new StockProductLevel("GPU", "PIM-G", "5901000000028", "X1", "Gigabyte X1", 1_000_000, 0, 3);
        MatchedInventory asusGroup = group("PIM-A", "5901000000011", "5901000000035");
        MatchedInventory gigabyteGroup = group("PIM-G", "5901000000028");

        when(storesRepository.findById("store")).thenReturn(store);
        when(store.isEnabledSupplier("Action")).thenReturn(true);
        when(inventory.withEnabledSuppliersOnly("store", SupplierScope.FULFILMENT)).thenReturn(enabledInventory);
        when(productCatalogRepository.findAll("store")).thenReturn(List.of(catalog));
        when(catalog.getCatalogId()).thenReturn("cat");
        when(stockLevels.calculate("store", "cat", null, RestockScope.WholeCatalog, false, true))
                .thenReturn(List.of(asus, gigabyte));
        when(enabledInventory.findByInventoryKey(any()))
                .thenAnswer(call -> "PIM-A".equals(call.<InventoryKey>getArgument(0).getId()) ? asusGroup : gigabyteGroup);
        when(warehouse.stockQueryService("store")).thenReturn(stockQueryService);
        when(stockQueryService.searchByEans(eq("store"), any())).thenReturn(List.of());

        // when
        List<RestockSuggestion> result = service.suggestForDelivery("store", "Action", Set.of("5901000000035"));

        // then
        assertThat(result).extracting(RestockSuggestion::getName).containsExactly("Gigabyte X1");
    }

    private static MatchedInventory group(String pimId, String... eans) {
        InventoryKey key = InventoryKey.fromPimIdAndEan(pimId, eans[0]);
        Stream.of(eans).forEach(key::addEan);
        return new MatchedInventory(key, List.of(new InventoryItem(eans[0], "X1", 100.0, "PLN", 5, 1, "Action")), null, null);
    }

    @Test
    void skipsOffersWithoutHistoricalThresholds() {
        // given
        StockProductLevel level = level("CPU", "MFN-1", "AMD Ryzen", 0);
        InventoryItem offer = new InventoryItem("EAN", "MFN", 100.0, "PLN", 5, 1, "Action");

        when(storesRepository.findById("store")).thenReturn(store);
        when(store.isEnabledSupplier("Action")).thenReturn(true);
        when(inventory.withEnabledSuppliersOnly("store", SupplierScope.FULFILMENT)).thenReturn(enabledInventory);
        when(productCatalogRepository.findAll("store")).thenReturn(List.of(catalog));
        when(catalog.getCatalogId()).thenReturn("cat");
        when(stockLevels.calculate("store", "cat", null, RestockScope.WholeCatalog, false, true))
                .thenReturn(List.of(level));
        when(enabledInventory.findByInventoryKey(any())).thenReturn(matched);
        when(matched.isEmpty()).thenReturn(false);
        when(matched.getInventoryKey()).thenReturn(new InventoryKey());
        when(matched.getInventoryItemsFromSupplier("Action")).thenReturn(List.of(offer));

        // when
        List<RestockSuggestion> result = service.suggestForDelivery("store", "Action", Collections.emptySet());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void restockWithBudgetKeepsOnlyOffersWithinThreshold() {
        // given
        StockProductLevel affordable = level("CPU", "MFN-1", "AMD Ryzen", 200);
        StockProductLevel tooExpensive = level("CPU", "MFN-2", "Intel Core", 99);

        InventoryItem offer = new InventoryItem("EAN", "MFN", 100.0, "PLN", 5, 1, "Action");

        when(inventory.withEnabledSuppliersOnly("store", SupplierScope.FULFILMENT)).thenReturn(enabledInventory);
        when(stockLevels.calculate("store", "cat", null, RestockScope.WholeCatalog, false, false))
                .thenReturn(List.of(affordable, tooExpensive));
        when(enabledInventory.findByInventoryKey(any())).thenReturn(matched);
        when(matched.isEmpty()).thenReturn(false);
        when(matched.getInventoryKey()).thenReturn(new InventoryKey());
        when(matched.getInventoryItems()).thenReturn(List.of(offer));

        // when
        List<RestockSuggestion> result = service.suggestForRestock("store", "cat", null, RestockScope.WholeCatalog, false, RestockPriceCategory.GoodDeal);

        // then
        assertThat(result).extracting(RestockSuggestion::getName).containsExactly("AMD Ryzen");
    }

    @Test
    void restockComparesTheOfferNetPriceWithTheNetLimit() {
        // given
        StockProductLevel level = level("CPU", "MFN-1", "AMD Ryzen", 100);
        InventoryItem offer = new InventoryItem("EAN", "MFN", 90.0, "PLN", 5, 1, "Action");

        when(inventory.withEnabledSuppliersOnly("store", SupplierScope.FULFILMENT)).thenReturn(enabledInventory);
        when(stockLevels.calculate("store", "cat", null, RestockScope.WholeCatalog, false, false))
                .thenReturn(List.of(level));
        when(enabledInventory.findByInventoryKey(any())).thenReturn(matched);
        when(matched.isEmpty()).thenReturn(false);
        when(matched.getInventoryKey()).thenReturn(new InventoryKey());
        when(matched.getInventoryItems()).thenReturn(List.of(offer));

        // when
        List<RestockSuggestion> result = service.suggestForRestock("store", "cat", null, RestockScope.WholeCatalog, false, RestockPriceCategory.GoodDeal);

        // then
        assertThat(result).extracting(RestockSuggestion::getPriceCategory).containsExactly(RestockPriceCategory.GoodDeal);
    }

    @Test
    void restockWithoutBudgetIncludesItemsWithoutOffers() {
        // given
        StockProductLevel level = level("CPU", "MFN-1", "AMD Ryzen", 0);

        when(inventory.withEnabledSuppliersOnly("store", SupplierScope.FULFILMENT)).thenReturn(enabledInventory);
        when(stockLevels.calculate("store", "cat", null, RestockScope.WholeCatalog, false, false))
                .thenReturn(List.of(level));
        when(enabledInventory.findByInventoryKey(any())).thenReturn(null);

        // when
        List<RestockSuggestion> result = service.suggestForRestock("store", "cat", null, RestockScope.WholeCatalog, false, null);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).hasOffer()).isFalse();
        assertThat(result.get(0).getPriceCategory()).isNull();
    }

    @Test
    void suggestionLooksUpTheGroupOfTheProduct() {
        // given
        StockProductLevel level = new StockProductLevel("CPU", "PIM-1", "5900000000014", "MFN-1", "AMD Ryzen", 0, 0, 3);

        when(inventory.withEnabledSuppliersOnly("store", SupplierScope.FULFILMENT)).thenReturn(enabledInventory);
        when(stockLevels.calculate("store", "cat", null, RestockScope.WholeCatalog, false, false))
                .thenReturn(List.of(level));
        when(enabledInventory.findByInventoryKey(any())).thenReturn(null);

        // when
        service.suggestForRestock("store", "cat", null, RestockScope.WholeCatalog, false, null);

        // then
        ArgumentCaptor<InventoryKey> key = ArgumentCaptor.forClass(InventoryKey.class);
        verify(enabledInventory).findByInventoryKey(key.capture());
        assertThat(key.getValue().getId()).isEqualTo("PIM-1");
        assertThat(key.getValue().getProductEans()).containsExactly("5900000000014");
        assertThat(key.getValue().getProductCodes()).isEmpty();
    }
}
