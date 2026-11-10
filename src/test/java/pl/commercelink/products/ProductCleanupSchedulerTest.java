package pl.commercelink.products;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCleanupSchedulerTest {

    private static final String STORE_ID = "store-1";
    private static final String CATEGORY_ID = "category-1";

    @Mock private ProductCatalogRepository productCatalogRepository;
    @Mock private ProductRepository productRepository;
    @Mock private StoresRepository storesRepository;

    @InjectMocks
    private ProductCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "cleanupDays", 7);
    }

    @Test
    void cleansUpTheProductsOfAnInactiveStoreToo() {
        // given
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
        when(productCatalogRepository.findByIdConsistently("inactive", catalog.getCatalogId())).thenReturn(catalog);

        // when
        scheduler.cleanUpOrphanedProducts("tick");

        // then
        verify(productRepository).deleteWhateverItsVersion(leftOver.get(0));
    }

    @Test
    void deletesTheProductsOfACategoryStillDynamicPastTheRetention() {
        // given
        ProductCatalog read = catalogWith(category(CategoryDefinitionType.Dynamic, 8));
        List<Product> leftOver = List.of(new Product(CATEGORY_ID));
        storeHolds(read);
        when(productRepository.findAll(CATEGORY_ID)).thenReturn(leftOver);
        when(productCatalogRepository.findByIdConsistently(STORE_ID, read.getCatalogId()))
                .thenReturn(sameCatalogWith(read, category(CategoryDefinitionType.Dynamic, 8)));

        // when
        scheduler.cleanUpOrphanedProducts("tick");

        // then
        verify(productRepository).deleteWhateverItsVersion(leftOver.get(0));
    }

    @Test
    void keepsTheProductsOfACategoryThatBecameManagedAfterTheRunReadTheCatalog() {
        // given: turned manual while the run went through the stores, and given its products then
        ProductCatalog read = catalogWith(category(CategoryDefinitionType.Dynamic, 8));
        storeHolds(read);
        when(productRepository.findAll(CATEGORY_ID)).thenReturn(List.of(new Product(CATEGORY_ID)));
        when(productCatalogRepository.findByIdConsistently(STORE_ID, read.getCatalogId()))
                .thenReturn(sameCatalogWith(read, category(CategoryDefinitionType.Managed, 0)));

        // when
        scheduler.cleanUpOrphanedProducts("tick");

        // then
        verify(productRepository, never()).deleteWhateverItsVersion(any());
    }

    @Test
    void keepsTheProductsOfACategoryTurnedDynamicAgainWithinTheRetention() {
        // given: manual and back to automatic a moment ago, so the retention starts again
        ProductCatalog read = catalogWith(category(CategoryDefinitionType.Dynamic, 8));
        storeHolds(read);
        when(productRepository.findAll(CATEGORY_ID)).thenReturn(List.of(new Product(CATEGORY_ID)));
        when(productCatalogRepository.findByIdConsistently(STORE_ID, read.getCatalogId()))
                .thenReturn(sameCatalogWith(read, category(CategoryDefinitionType.Dynamic, 0)));

        // when
        scheduler.cleanUpOrphanedProducts("tick");

        // then
        verify(productRepository, never()).deleteWhateverItsVersion(any());
    }

    @Test
    void keepsTheProductsOfACategoryRemovedMeanwhile() {
        // given
        ProductCatalog read = catalogWith(category(CategoryDefinitionType.Dynamic, 8));
        storeHolds(read);
        when(productRepository.findAll(CATEGORY_ID)).thenReturn(List.of(new Product(CATEGORY_ID)));
        when(productCatalogRepository.findByIdConsistently(STORE_ID, read.getCatalogId()))
                .thenReturn(sameCatalogWith(read));

        // when
        scheduler.cleanUpOrphanedProducts("tick");

        // then
        verify(productRepository, never()).deleteWhateverItsVersion(any());
    }

    @Test
    void keepsTheProductsOfACatalogRemovedMeanwhile() {
        // given
        ProductCatalog read = catalogWith(category(CategoryDefinitionType.Dynamic, 8));
        storeHolds(read);
        when(productRepository.findAll(CATEGORY_ID)).thenReturn(List.of(new Product(CATEGORY_ID)));
        when(productCatalogRepository.findByIdConsistently(STORE_ID, read.getCatalogId())).thenReturn(null);

        // when
        scheduler.cleanUpOrphanedProducts("tick");

        // then
        verify(productRepository, never()).deleteWhateverItsVersion(any());
    }

    @Test
    void readsTheProductsBeforeCheckingTheCategoryAgain() {
        // given
        ProductCatalog read = catalogWith(category(CategoryDefinitionType.Dynamic, 8));
        storeHolds(read);
        when(productRepository.findAll(CATEGORY_ID)).thenReturn(List.of(new Product(CATEGORY_ID)));
        when(productCatalogRepository.findByIdConsistently(STORE_ID, read.getCatalogId()))
                .thenReturn(sameCatalogWith(read, category(CategoryDefinitionType.Dynamic, 8)));

        // when
        scheduler.cleanUpOrphanedProducts("tick");

        // then
        InOrder order = inOrder(productRepository, productCatalogRepository);
        order.verify(productRepository).findAll(CATEGORY_ID);
        order.verify(productCatalogRepository).findByIdConsistently(STORE_ID, read.getCatalogId());
        order.verify(productRepository).deleteWhateverItsVersion(any());
    }

    @Test
    void aCategoryWithNoProductsLeftIsNotReadAgain() {
        // given
        ProductCatalog read = catalogWith(category(CategoryDefinitionType.Dynamic, 8));
        storeHolds(read);
        when(productRepository.findAll(CATEGORY_ID)).thenReturn(List.of());

        // when
        scheduler.cleanUpOrphanedProducts("tick");

        // then
        verify(productCatalogRepository, never()).findByIdConsistently(STORE_ID, read.getCatalogId());
        verify(productRepository, never()).deleteWhateverItsVersion(any());
    }

    private void storeHolds(ProductCatalog catalog) {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        when(storesRepository.findAll()).thenReturn(List.of(store));
        when(productCatalogRepository.findAll(STORE_ID)).thenReturn(List.of(catalog));
    }

    private static CategoryDefinition category(CategoryDefinitionType type, int daysSinceTypeChange) {
        CategoryDefinition category = new CategoryDefinition();
        category.setCategoryId(CATEGORY_ID);
        category.setType(type);
        category.setTypeChangedAt(LocalDateTime.now().minusDays(daysSinceTypeChange));
        return category;
    }

    private static ProductCatalog catalogWith(CategoryDefinition... categories) {
        ProductCatalog catalog = new ProductCatalog(STORE_ID, "main");
        catalog.setCategories(List.of(categories));
        return catalog;
    }

    /** The same catalog as a later read returns it: same id, the categories it holds by then. */
    private static ProductCatalog sameCatalogWith(ProductCatalog catalog, CategoryDefinition... categories) {
        ProductCatalog fresh = catalogWith(categories);
        fresh.setCatalogId(catalog.getCatalogId());
        return fresh;
    }
}
