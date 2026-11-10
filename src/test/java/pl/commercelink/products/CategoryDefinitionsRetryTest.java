package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.starter.dynamodb.OptimisticLockingExhaustedException;
import pl.commercelink.testsupport.RetryingOptimisticLockingExecutor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CategoryDefinitions over the real Spring-Retry-proxied executor: what reaches the controller is the service's own
 * exception (a 404, a refused deletion, a conflict), never the proxy's ExhaustedRetryException.
 */
@ExtendWith(MockitoExtension.class)
class CategoryDefinitionsRetryTest {

    @Mock private ProductCatalogRepository catalogs;
    @Mock private ProductRepository products;
    @Mock private CatalogPlacement placement;
    @Mock private ManagedCategoryProducts managedCategoryProducts;
    private CategoryDefinitions definitions;

    private ProductCatalog catalog;
    private CategoryDefinition gpu;

    @BeforeEach
    void setUp() {
        definitions = new CategoryDefinitions(catalogs, products, RetryingOptimisticLockingExecutor.create(), placement,
                managedCategoryProducts);
        catalog = catalogWithGpu(false);
        gpu = catalog.getCategories().get(0);
    }

    private ProductCatalog catalogWithGpu(boolean protectedFromDeletion) {
        ProductCatalog read = new ProductCatalog("store", "Parts");
        read.setCatalogId("c1");
        CategoryDefinition category = new CategoryDefinition().withName("GPU").withSequenceNumber(1);
        category.setCategoryId("gpu");
        category.setDeletionProtection(protectedFromDeletion);
        read.getCategories().add(category);
        return read;
    }

    private ProductCatalog emptyCatalog() {
        ProductCatalog read = new ProductCatalog("store", "Parts");
        read.setCatalogId("c1");
        return read;
    }

    @Test
    void aConflictIsRetriedOnAFreshRead() {
        // given
        ProductCatalog fresh = catalogWithGpu(false);
        when(catalogs.findById("store", "c1")).thenReturn(catalogWithGpu(false), fresh);
        doThrow(new ConditionalCheckFailedException("version changed")).doNothing().when(catalogs).save(any());

        // when
        definitions.saveFilters(catalog, gpu, List.of());

        // then
        verify(catalogs, times(2)).save(any());
        verify(catalogs).save(fresh);
    }

    @Test
    void aSectionThatKeepsConflictingEndsInOptimisticLockingExhausted() {
        // given
        when(catalogs.findById("store", "c1")).thenAnswer(call -> catalogWithGpu(false));
        doThrow(new ConditionalCheckFailedException("version changed")).when(catalogs).save(any());

        // when / then
        assertThatThrownBy(() -> definitions.saveFilters(catalog, gpu, List.of()))
                .isInstanceOf(OptimisticLockingExhaustedException.class);
    }

    @Test
    void aCategoryRemovedMeanwhileIsNotFound() {
        // given
        when(catalogs.findById("store", "c1")).thenReturn(emptyCatalog());

        // when / then
        assertThatThrownBy(() -> definitions.savePricing(catalog, gpu, new CategoryDefinitions.Pricing(
                new StockDefinition(1, 10, 30), new AvailabilityDefinition(1, 1), List.of())))
                .isExactlyInstanceOf(CategoryDefinitions.CategoryNotFoundException.class);
        verify(catalogs, never()).save(any());
    }

    @Test
    void aCatalogRemovedMeanwhileIsNotFound() {
        // given
        when(catalogs.findById("store", "c1")).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> definitions.saveFilters(catalog, gpu, List.of()))
                .isExactlyInstanceOf(CategoryDefinitions.CategoryNotFoundException.class);
        verify(catalogs, never()).save(any());
    }

    @Test
    void aCategoryProtectedMeanwhileIsRefusedAndNothingIsDeleted() {
        // given
        when(catalogs.findById("store", "c1")).thenReturn(catalogWithGpu(true));

        // when / then
        assertThatThrownBy(() -> definitions.remove(catalog, gpu)).isExactlyInstanceOf(IllegalStateException.class);
        verify(catalogs, never()).save(any());
        verify(products, never()).findAll(any(String.class));
    }

    @Test
    void aRetryThatFindsTheCategoryAlreadyManagedCreatesNoProducts() {
        // given: the first save loses the race to another tab, which turned the category manual meanwhile
        ProductCatalog dynamic = catalogWithGpu(false);
        dynamic.getCategories().get(0).setType(CategoryDefinitionType.Dynamic);
        ProductCatalog managedMeanwhile = catalogWithGpu(false);
        when(catalogs.findById("store", "c1")).thenReturn(dynamic, managedMeanwhile);
        doThrow(new ConditionalCheckFailedException("version changed")).doNothing().when(catalogs).save(any());
        List<ManagedCategoryProducts.Candidate> list = List.of(new ManagedCategoryProducts.Candidate(
                new Product("gpu", "pim-1", "5900000000001", null, "MSI", "RTX", "MSI RTX", "Default"),
                InventoryKey.fromPimIdAndEan("pim-1", "5900000000001")));

        // when
        CategoryDefinitions.BasicsSaved saved = definitions.saveBasics(catalog, gpu, new CategoryDefinitions.Basics(
                "GPU", 1, List.of("pim-gpu"), CategoryDefinitionType.Managed, 1, false, false, false, List.of()), list);

        // then
        assertThat(saved).isEqualTo(CategoryDefinitions.BasicsSaved.UNCHANGED);
        verify(catalogs).save(managedMeanwhile);
        verify(managedCategoryProducts, never()).addMissing(any(), any(), any());
    }
}
