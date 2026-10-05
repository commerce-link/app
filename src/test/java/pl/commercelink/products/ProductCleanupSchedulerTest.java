package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCleanupSchedulerTest {

    @Mock private ProductCatalogRepository productCatalogRepository;
    @Mock private ProductRepository productRepository;
    @Mock private StoresRepository storesRepository;

    @InjectMocks
    private ProductCleanupScheduler scheduler;

    @Test
    void cleansUpTheProductsOfAnInactiveStoreToo() {
        // given
        ReflectionTestUtils.setField(scheduler, "cleanupDays", 7);
        Store store = new Store();
        store.setStoreId("inactive");
        store.setActive(false);
        CategoryDefinition switchedToDynamic = new CategoryDefinition();
        switchedToDynamic.setCategoryId("category-1");
        switchedToDynamic.setType(CategoryDefinitionType.Dynamic);
        switchedToDynamic.setTypeChangedAt(LocalDateTime.now().minusDays(8));
        ProductCatalog catalog = new ProductCatalog("inactive", "main");
        catalog.setCategories(List.of(switchedToDynamic));
        List<Product> leftOver = List.of(new Product());
        when(storesRepository.findAll()).thenReturn(List.of(store));
        when(productCatalogRepository.findAll("inactive")).thenReturn(List.of(catalog));
        when(productRepository.findAll("category-1")).thenReturn(leftOver);

        // when
        scheduler.cleanUpOrphanedProducts("tick");

        // then
        verify(productRepository).delete(leftOver);
    }
}
