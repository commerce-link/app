package pl.commercelink.inventory.deliveries;

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
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.warehouse.RestockScope;
import pl.commercelink.warehouse.StockLevels;
import pl.commercelink.warehouse.StockProductLevel;
import pl.commercelink.web.dtos.SuggestedDeliveryItem;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliverySuggestionServiceTest {

    @Mock
    private Inventory inventory;
    @Mock
    private StockLevels stockLevels;
    @Mock
    private ProductCatalogRepository productCatalogRepository;
    @Mock
    private StoresRepository storesRepository;

    @InjectMocks
    private DeliverySuggestionService service;

    @Mock
    private Store store;
    @Mock
    private InventoryView enabledInventory;
    @Mock
    private ProductCatalog catalog;
    @Mock
    private MatchedInventory matched;

    @Test
    void sortsSuggestionsByCategoryThenName() {
        // given
        StockProductLevel gpu = new StockProductLevel(ProductCategory.GPU, "MFN-GPU", "Zotac RTX", 1_000_000, 0, 3);
        StockProductLevel cpuAmd = new StockProductLevel(ProductCategory.CPU, "MFN-CPU-AMD", "AMD Ryzen", 1_000_000, 0, 3);
        StockProductLevel cpuIntel = new StockProductLevel(ProductCategory.CPU, "MFN-CPU-INTEL", "Intel Core", 1_000_000, 0, 3);

        InventoryItem offer = new InventoryItem("EAN", "MFN", 100.0, "PLN", 5, 1, "Action");

        when(storesRepository.findById("store")).thenReturn(store);
        when(store.isEnabledSupplier("Action")).thenReturn(true);
        when(inventory.withEnabledSuppliersOnly("store", SupplierScope.FULFILMENT)).thenReturn(enabledInventory);
        when(productCatalogRepository.findAll("store")).thenReturn(List.of(catalog));
        when(catalog.getCatalogId()).thenReturn("cat");
        when(stockLevels.calculate("store", "cat", null, RestockScope.ExpectedStockQty, true))
                .thenReturn(List.of(gpu, cpuAmd, cpuIntel));
        when(enabledInventory.findByProductCode(anyString())).thenReturn(matched);
        when(matched.isEmpty()).thenReturn(false);
        when(matched.hasOffersFrom("Action")).thenReturn(true);
        when(matched.getInventoryItemsFromSupplier("Action")).thenReturn(List.of(offer));
        when(matched.getInventoryItems()).thenReturn(List.of(offer));

        // when
        List<SuggestedDeliveryItem> result = service.suggestFor("store", "Action", Collections.emptySet());

        // then
        assertThat(result).hasSize(3);
        assertThat(result).extracting(SuggestedDeliveryItem::getCategory)
                .containsExactly("CPU", "CPU", "GPU");
        assertThat(result).extracting(SuggestedDeliveryItem::getName)
                .containsExactly("AMD Ryzen", "Intel Core", "Zotac RTX");
    }
}
