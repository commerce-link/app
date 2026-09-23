package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.products.filters.InventoryFilterType;
import pl.commercelink.starter.dynamodb.Metadata;
import pl.commercelink.starter.dynamodb.OptimisticLockingExecutor;
import pl.commercelink.starter.dynamodb.OptimisticLockingExhaustedException;
import pl.commercelink.testsupport.OptimisticLockingExecutorMocks;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryDefinitionsTest {

    @Mock
    private ProductCatalogRepository catalogs;
    @Mock
    private ProductRepository products;
    @Mock
    private OptimisticLockingExecutor optimisticLockingExecutor;
    @InjectMocks
    private CategoryDefinitions definitions;

    private ProductCatalog catalog;
    private CategoryDefinition gpu;

    @BeforeEach
    void setUp() {
        catalog = new ProductCatalog("store", "Parts");
        gpu = new CategoryDefinition().withName("GPU").withGeneratedId().withSequenceNumber(1)
                .withStockDefinition(new StockDefinition(2, 4, 10))
                .withAvailabilityDefinition(new AvailabilityDefinition(3, 1))
                .withPriceDefinition(new PriceDefinition(1.05, 49, 0, 0, 0, "Default"))
                .withMarketplaceDefinition(new MarketplaceDefinition("allegro", 1.1, 0, 1, 2, 0, 3));
        gpu.getInventoryDefinitions().add(new InventoryDefinition(InventoryFilterType.BRAND_NAME, List.of(new Metadata("Brands", "MSI"))));
        catalog.getCategories().add(gpu);
        // Every section is applied to the catalog as read at the save; here that read answers the same object, so the
        // tests below can look at the category they passed in.
        lenient().when(catalogs.findById(catalog.getStoreId(), catalog.getCatalogId())).thenReturn(catalog);
        // The answer behaves like the real proxy: conflicts retried, anything else wrapped (RetryingOptimisticLockingExecutorTest).
        lenient().when(optimisticLockingExecutor.modifyAndSave(any(), any(), any()))
                .thenAnswer(OptimisticLockingExecutorMocks.retryingModifyAndSave(3));
    }

    /** The catalog as another request left it: the same category, renamed by a save of the basics. */
    private ProductCatalog renamedMeanwhile() {
        ProductCatalog fresh = new ProductCatalog("store", "Parts");
        fresh.setCatalogId(catalog.getCatalogId());
        CategoryDefinition renamed = new CategoryDefinition().withName("Karty graficzne").withSequenceNumber(1)
                .withStockDefinition(new StockDefinition(2, 4, 10))
                .withAvailabilityDefinition(new AvailabilityDefinition(3, 1))
                .withPriceDefinition(new PriceDefinition(1.05, 49, 0, 0, 0, "Default"));
        renamed.setCategoryId(gpu.getCategoryId());
        fresh.getCategories().add(renamed);
        return fresh;
    }

    private static CategoryDefinitions.Pricing premiumPricing() {
        return new CategoryDefinitions.Pricing(new StockDefinition(1, 10, 30), new AvailabilityDefinition(1, 1),
                List.of(new PriceDefinition(1.0, 0, 0, 0, 0, "Default"), new PriceDefinition(1.1, 99, 30, 20, 10, "Premium")));
    }

    @Test
    void savingPricingRetriesAfterAConcurrentSaveOfBasics() {
        // given -- the first save loses the race to a save of the basics; the retry reads the catalog that save left
        ProductCatalog afterBasics = renamedMeanwhile();
        when(catalogs.findById(catalog.getStoreId(), catalog.getCatalogId())).thenReturn(catalog, afterBasics);
        doThrow(new ConditionalCheckFailedException("version changed")).doNothing().when(catalogs).save(any());
        doAnswer(OptimisticLockingExecutorMocks.retryingModifyAndSave(3))
                .when(optimisticLockingExecutor).modifyAndSave(any(), any(), any());

        // when
        definitions.savePricing(catalog, gpu, premiumPricing());

        // then -- the pricing lands on the fresh read, next to the basics the other request saved
        ArgumentCaptor<ProductCatalog> saved = ArgumentCaptor.forClass(ProductCatalog.class);
        verify(catalogs, times(2)).save(saved.capture());
        CategoryDefinition stored = saved.getAllValues().get(1).getCategories().get(0);
        assertThat(saved.getAllValues().get(1)).isSameAs(afterBasics);
        assertThat(stored.getName()).isEqualTo("Karty graficzne");
        assertThat(stored.getPriceDefinitions()).extracting(PriceDefinition::getPricingGroup)
                .containsExactlyInAnyOrder("Default", "Premium");
        assertThat(stored.getStockDefinition().getHighStockThreshold()).isEqualTo(30);
    }

    @Test
    void aSectionThatKeepsLosingTheRaceEndsInOptimisticLockingExhausted() {
        // given
        doThrow(new ConditionalCheckFailedException("version changed")).when(catalogs).save(any());
        doAnswer(OptimisticLockingExecutorMocks.retryingModifyAndSave(3))
                .when(optimisticLockingExecutor).modifyAndSave(any(), any(), any());

        // when / then
        assertThatThrownBy(() -> definitions.saveFilters(catalog, gpu, List.of()))
                .isInstanceOf(OptimisticLockingExhaustedException.class);
        verify(catalogs, times(3)).save(catalog);
    }

    @Test
    void aSectionOfACategoryRemovedMeanwhileIsNotFound() {
        // given
        ProductCatalog withoutGpu = new ProductCatalog("store", "Parts");
        withoutGpu.setCatalogId(catalog.getCatalogId());
        when(catalogs.findById(catalog.getStoreId(), catalog.getCatalogId())).thenReturn(withoutGpu);

        // when / then
        assertThatThrownBy(() -> definitions.savePricing(catalog, gpu, premiumPricing()))
                .isExactlyInstanceOf(CategoryDefinitions.CategoryNotFoundException.class);
        verify(catalogs, never()).save(any());
    }

    @Test
    void creatingACategoryRetriedAfterAConflictAddsItOnceUnderOneId() {
        // given
        ProductCatalog afterBasics = renamedMeanwhile();
        when(catalogs.findById(catalog.getStoreId(), catalog.getCatalogId())).thenReturn(catalog, afterBasics);
        doThrow(new ConditionalCheckFailedException("version changed")).doNothing().when(catalogs).save(any());
        doAnswer(OptimisticLockingExecutorMocks.retryingModifyAndSave(3))
                .when(optimisticLockingExecutor).modifyAndSave(any(), any(), any());

        // when
        CategoryDefinition created = definitions.create(catalog, new CategoryDefinitions.Basics(
                "CPU", 0, List.of(), CategoryDefinitionType.Managed, 1, false, false, true, List.of()));

        // then
        assertThat(afterBasics.getCategories()).extracting(CategoryDefinition::getCategoryId)
                .containsExactly(gpu.getCategoryId(), created.getCategoryId());
        assertThat(created.getSequenceNumber()).isEqualTo(2);
    }

    @Test
    void removeSavesTheCatalogBeforeItDeletesTheProducts() {
        // given
        gpu.setDeletionProtection(false);
        List<Product> owned = List.of(new Product(gpu.getCategoryId(), "p1", "1", "m", "b", "l", "n", "Default"));
        when(products.findAll(gpu.getCategoryId())).thenReturn(owned);

        // when
        definitions.remove(catalog, gpu);

        // then
        InOrder order = inOrder(catalogs, products);
        order.verify(catalogs).save(catalog);
        order.verify(products).deleteWhateverItsVersion(owned.get(0));
    }

    @Test
    void aRemovalWhoseCatalogSaveConflictsLeavesTheProductsUntouched() {
        // given -- every attempt reads the catalog anew, as DynamoDB would answer it
        gpu.setDeletionProtection(false);
        when(catalogs.findById(catalog.getStoreId(), catalog.getCatalogId())).thenAnswer(call -> {
            ProductCatalog read = new ProductCatalog("store", "Parts");
            read.setCatalogId(catalog.getCatalogId());
            CategoryDefinition stored = new CategoryDefinition().withName("GPU").withSequenceNumber(1);
            stored.setCategoryId(gpu.getCategoryId());
            stored.setDeletionProtection(false);
            read.getCategories().add(stored);
            return read;
        });
        doThrow(new ConditionalCheckFailedException("version changed")).when(catalogs).save(any());
        doAnswer(OptimisticLockingExecutorMocks.retryingModifyAndSave(3))
                .when(optimisticLockingExecutor).modifyAndSave(any(), any(), any());

        // when / then
        assertThatThrownBy(() -> definitions.remove(catalog, gpu)).isInstanceOf(OptimisticLockingExhaustedException.class);
        verify(catalogs, times(3)).save(any());
        verify(products, never()).findAll(any(String.class));
        verify(products, never()).deleteWhateverItsVersion(any());
    }

    @Test
    void createUsesTheDefaultPricingAndTheNextSequenceNumber() {
        // when
        CategoryDefinition created = definitions.create(catalog, new CategoryDefinitions.Basics(
                "CPU", 0, List.of("pim-cpu"), CategoryDefinitionType.Managed, 1, false, false, true, List.of()));

        // then
        assertThat(created.getCategoryId()).isNotBlank();
        assertThat(created.getSequenceNumber()).isEqualTo(2);
        assertThat(created.getStockDefinition().getCriticalStockThreshold()).isEqualTo(1);
        assertThat(created.getStockDefinition().getHighStockThreshold()).isEqualTo(30);
        assertThat(created.getAvailabilityDefinition().getTotalMinQty()).isEqualTo(3);
        assertThat(created.getPriceDefinitions()).extracting(PriceDefinition::getPricingGroup).containsExactly("Default");
        assertThat(created.isComplete()).isTrue();
        assertThat(catalog.getCategories()).contains(created);
        verify(catalogs).save(catalog);
    }

    @Test
    void saveBasicsLeavesPricingMarketplacesAndFiltersUntouched() {
        // when
        definitions.saveBasics(catalog, gpu, new CategoryDefinitions.Basics(
                "Karty graficzne", 5, List.of("pim-gpu"), CategoryDefinitionType.Managed, 3, true, false, false, List.of("RTX 5060", "", "RTX 5070", "RTX 5060")));

        // then
        assertThat(gpu.getName()).isEqualTo("Karty graficzne");
        assertThat(gpu.getSequenceNumber()).isEqualTo(5);
        assertThat(gpu.getMaxQty()).isEqualTo(3);
        assertThat(gpu.isRequiredDuringOrder()).isTrue();
        assertThat(gpu.isDeletionProtection()).isFalse();
        assertThat(gpu.getGroupingOrder()).containsExactly("RTX 5060", "RTX 5070");
        assertThat(gpu.getPimCategoryIds()).containsExactly("pim-gpu");
        assertThat(gpu.getTypeChangedAt()).isNull();
        assertThat(gpu.getPriceDefinitions()).hasSize(1);
        assertThat(gpu.getMarketplaceDefinitions()).hasSize(1);
        assertThat(gpu.getInventoryDefinitions()).hasSize(1);
        verify(catalogs).save(catalog);
    }

    @Test
    void changingTheTypeStampsTypeChangedAt() {
        // when
        definitions.saveBasics(catalog, gpu, new CategoryDefinitions.Basics(
                "GPU", 1, List.of(), CategoryDefinitionType.Dynamic, 1, false, false, true, List.of()));

        // then
        assertThat(gpu.getType()).isEqualTo(CategoryDefinitionType.Dynamic);
        assertThat(gpu.getTypeChangedAt()).isNotNull();
    }

    @Test
    void savePricingReplacesOnlyThePricingSection() {
        // when
        definitions.savePricing(catalog, gpu, premiumPricing());

        // then
        assertThat(gpu.getPriceDefinitions()).extracting(PriceDefinition::getPricingGroup).containsExactlyInAnyOrder("Default", "Premium");
        assertThat(gpu.getStockDefinition().getHighStockThreshold()).isEqualTo(30);
        assertThat(gpu.getName()).isEqualTo("GPU");
        assertThat(gpu.getMarketplaceDefinitions()).hasSize(1);
    }

    @Test
    void saveMarketplaceReplacesTheDefinitionWithTheSameName() {
        // when
        definitions.saveMarketplace(catalog, gpu, new MarketplaceDefinition("allegro", 1.2, 0, 1, 3, 1, 0));
        definitions.saveMarketplace(catalog, gpu, new MarketplaceDefinition("empik", 1.1, 0, 1, 2, 0, 0));

        // then
        assertThat(gpu.getMarketplaceDefinitions()).hasSize(2);
        assertThat(gpu.getCategoryDefinition("allegro")).get().extracting(MarketplaceDefinition::getMarkup).isEqualTo(1.2);
    }

    @Test
    void removeMarketplaceDropsUnnamedDefinitionsWhenAskedForNull() {
        // given
        gpu.getMarketplaceDefinitions().add(new MarketplaceDefinition(null, 1.0, 0, 5, 3, 0, 0));

        // when
        definitions.removeMarketplace(catalog, gpu, null);

        // then
        assertThat(gpu.getMarketplaceDefinitions()).extracting(MarketplaceDefinition::getName).containsExactly("allegro");
    }

    @Test
    void removeDeletesProductsUnlessAnotherCategorySharesAPimCategory() {
        // given
        gpu.setDeletionProtection(false);
        gpu.setPimCategoryIds(List.of("pim-gpu"));
        CategoryDefinition twin = new CategoryDefinition().withName("GPU 2").withGeneratedId();
        twin.setPimCategoryIds(List.of("pim-gpu"));
        catalog.getCategories().add(twin);

        // when
        CategoryDefinitions.RemoveResult result = definitions.remove(catalog, gpu);

        // then
        assertThat(result.productsKept()).isTrue();
        assertThat(catalog.getCategories()).doesNotContain(gpu);
        verify(products, never()).deleteWhateverItsVersion(any());
    }

    @Test
    void deletionPreviewSaysProductsAreKeptExactlyWhenRemoveKeepsThem() {
        // given
        gpu.setDeletionProtection(false);
        gpu.setPimCategoryIds(List.of("pim-gpu"));
        CategoryDefinition twin = new CategoryDefinition().withName("GPU 2").withGeneratedId();
        twin.setPimCategoryIds(List.of("pim-gpu"));
        catalog.getCategories().add(twin);

        // when
        CategoryDefinitions.DeletionPreview preview = definitions.deletionPreview(catalog, gpu);
        CategoryDefinitions.RemoveResult result = definitions.remove(catalog, gpu);

        // then
        assertThat(preview.productsKept()).isTrue();
        assertThat(preview.productsToDelete()).isZero();
        assertThat(result.productsKept()).isEqualTo(preview.productsKept());
        verify(products, never()).deleteWhateverItsVersion(any());
    }

    @Test
    void deletionPreviewCountsTheProductsRemoveWillDeleteWhenNoOtherCategoryOwnsThem() {
        // given
        gpu.setDeletionProtection(false);
        gpu.setPimCategoryIds(List.of("pim-gpu"));
        List<Product> owned = List.of(
                new Product(gpu.getCategoryId(), "p1", "1", "m", "b", "l", "n", "Default"),
                new Product(gpu.getCategoryId(), "p2", "2", "m", "b", "l", "n", "Default"));
        when(products.findAll(gpu.getCategoryId())).thenReturn(owned);

        // when
        CategoryDefinitions.DeletionPreview preview = definitions.deletionPreview(catalog, gpu);
        CategoryDefinitions.RemoveResult result = definitions.remove(catalog, gpu);

        // then
        assertThat(preview.productsKept()).isFalse();
        assertThat(preview.productsToDelete()).isEqualTo(2);
        assertThat(result.productsKept()).isEqualTo(preview.productsKept());
        assertThat(result.productsDeleted()).isEqualTo(preview.productsToDelete());
        owned.forEach(product -> verify(products).deleteWhateverItsVersion(product));
    }

    @Test
    void protectedCategoryCannotBeRemoved() {
        // when / then
        assertThatThrownBy(() -> definitions.remove(catalog, gpu)).isInstanceOf(IllegalStateException.class);
        verify(catalogs, never()).save(any());
    }

    @Test
    void countsProductsUsingAPriceGroup() {
        // given
        when(products.findAll(gpu.getCategoryId())).thenReturn(List.of(
                new Product(gpu.getCategoryId(), "p1", "1", "m", "b", "l", "n", "Premium"),
                new Product(gpu.getCategoryId(), "p2", "2", "m", "b", "l", "n", "Default")));

        // when / then
        assertThat(definitions.productsInPriceGroup(gpu, "Premium")).isEqualTo(1);
        assertThat(definitions.productsInPriceGroup(gpu, "Ultra")).isZero();
    }

    /** Protection switched on by another request between the page's check and the save: refused, nothing deleted. */
    @Test
    void aCategoryProtectedMeanwhileIsRefusedAndNothingIsDeleted() {
        // given
        gpu.setDeletionProtection(false);
        ProductCatalog read = renamedMeanwhile();
        read.getCategories().get(0).setDeletionProtection(true);
        when(catalogs.findById(catalog.getStoreId(), catalog.getCatalogId())).thenReturn(read);

        // when / then
        assertThatThrownBy(() -> definitions.remove(catalog, gpu)).isExactlyInstanceOf(IllegalStateException.class);
        verify(catalogs, never()).save(any());
        verify(products, never()).findAll(any(String.class));
    }

    @Test
    void aCatalogRemovedMeanwhileIsNotFound() {
        // given
        when(catalogs.findById(catalog.getStoreId(), catalog.getCatalogId())).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> definitions.saveFilters(catalog, gpu, List.of()))
                .isExactlyInstanceOf(CategoryDefinitions.CategoryNotFoundException.class);
    }
}
