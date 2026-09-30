package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCleanupSchedulerTest {

    @Mock private ProductCatalogRepository productCatalogRepository;
    @Mock private ProductRepository productRepository;
    @Mock private StoresRepository storesRepository;
    @Mock private StoreActivity storeActivity;

    @InjectMocks
    private ProductCleanupScheduler scheduler;

    private static Store store(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        return store;
    }

    @Test
    void leavesProductsOfInactiveStoreAlone() {
        // given
        Store active = store("active");
        Store inactive = store("inactive");
        when(storesRepository.findAll()).thenReturn(List.of(active, inactive));
        when(storeActivity.isActive(active)).thenReturn(true);
        when(storeActivity.isActive(inactive)).thenReturn(false);
        when(productCatalogRepository.findAll("active")).thenReturn(List.of());

        // when
        scheduler.cleanUpOrphanedProducts("tick");

        // then
        verify(productCatalogRepository).findAll("active");
        verify(productCatalogRepository, never()).findAll("inactive");
    }
}
