package pl.commercelink.products.information;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRecommendationEngine;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PIMIndexEventSchedulerTest {

    @Mock private PimCatalog pimCatalog;
    @Mock private ProductRepository productRepository;
    @Mock private ProductCatalogRepository productCatalogRepository;
    @Mock private StoresRepository storesRepository;
    @Mock private Inventory inventory;
    @Mock private ProductRecommendationEngine recommendationEngine;
    @Mock private StoreActivity storeActivity;

    @InjectMocks
    private PIMIndexEventScheduler scheduler;

    private static Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        return store;
    }

    @Test
    void scansProductsOfActiveStoresOnly() {
        // given
        Store active = store("active");
        Store inactive = store("inactive");
        when(storesRepository.findAll()).thenReturn(List.of(active, inactive));
        when(storeActivity.isActive(active)).thenReturn(true);
        when(storeActivity.isActive(inactive)).thenReturn(false);
        when(productCatalogRepository.findAll("active")).thenReturn(List.of());

        // when
        scheduler.scanAndStoreProductsInPimQueue("tick");

        // then
        verify(productCatalogRepository).findAll("active");
        verify(productCatalogRepository, never()).findAll("inactive");
    }
}
