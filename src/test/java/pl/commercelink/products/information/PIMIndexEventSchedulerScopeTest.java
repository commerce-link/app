package pl.commercelink.products.information;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PIMIndexEventSchedulerScopeTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductCatalogRepository productCatalogRepository;
    @Mock private StoresRepository storesRepository;
    @Mock private Inventory inventory;
    @Mock private InventoryView inventoryView;
    @Mock private ProductRecommendationEngine recommendationEngine;
    @Mock private StoreActivity storeActivity;

    private static Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        return store;
    }

    @Test
    void productWithAStoreEntryIsNotSubmitted() {
        // given
        PimCatalog pimCatalog = ScopedPimCatalog.of(
                ScopedPimCatalog.storeEntry("s1-2", "s1", ScopedPimCatalog.STORE_GTIN, "MPN-S1", "GPU"));
        PIMIndexEventScheduler scheduler = new PIMIndexEventScheduler(pimCatalog, productRepository,
                productCatalogRepository, storesRepository, inventory, recommendationEngine, storeActivity);
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withGeneratedId();
        ProductCatalog catalog = new ProductCatalog("any", "main");
        catalog.addOrUpdateCategoryDefinition(category);
        Product product = new Product(category.getCategoryId(), null, ScopedPimCatalog.STORE_GTIN, "MPN-S1", "Brand", "l", "n", "Default");
        when(storeActivity.isActive(any(Store.class))).thenReturn(true);
        when(productCatalogRepository.findAll(any())).thenReturn(List.of(catalog));
        when(productRepository.findAll(category.getCategoryId())).thenReturn(List.of(product));
        when(inventory.withEnabledSuppliersOnly(any())).thenReturn(inventoryView);

        // when
        when(storesRepository.findAll()).thenReturn(List.of(store("s1")));
        scheduler.scanAndStoreProductsInPimQueue("tick");
        verify(pimCatalog, never()).submit(any());
        when(storesRepository.findAll()).thenReturn(List.of(store("s2")));
        scheduler.scanAndStoreProductsInPimQueue("tick");

        // then
        verify(pimCatalog, org.mockito.Mockito.times(1)).submit(any());
    }
}
