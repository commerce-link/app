package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.stores.SupplierScope;

import java.util.LinkedList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicCategoryProductsTest {

    private static final String STORE_ID = "store-1";

    @Mock private ProductRecommendationEngine recommendationEngine;
    @Mock private PimCatalog pimCatalog;
    @Mock private Inventory inventory;
    @Mock private PimView pimView;
    @Mock private InventoryView pricingView;
    @Mock private ProductRecommendation recommendation;

    @InjectMocks
    private DynamicCategoryProducts dynamicCategoryProducts;

    @Test
    void candidatesComeFromThePricelistViewOfTheInventory() {
        // given
        CategoryDefinition category = mappedTo("170");
        when(pimCatalog.withStoreData(STORE_ID)).thenReturn(pimView);
        when(inventory.withEnabledSuppliersAndWarehouseData(STORE_ID, SupplierScope.PRICING)).thenReturn(pricingView);
        when(recommendationEngine.getRecommendationsForMappedProducts(pimView, category, pricingView)).thenReturn(List.of());

        // when
        List<ManagedCategoryProducts.Candidate> candidates = dynamicCategoryProducts.candidates(STORE_ID, category);

        // then
        assertThat(candidates).isEmpty();
    }

    @Test
    void eachRecommendationBecomesACandidateWithItsInventoryKey() {
        // given
        CategoryDefinition category = mappedTo("170");
        Product product = new Product(category.getCategoryId(), "pim-1", "5900000000001", "MFN-1", "MSI", "RTX 5070",
                "MSI RTX 5070", "Default");
        InventoryKey key = InventoryKey.fromPimIdAndEan("pim-1", "5900000000001");
        when(pimCatalog.withStoreData(STORE_ID)).thenReturn(pimView);
        when(inventory.withEnabledSuppliersAndWarehouseData(STORE_ID, SupplierScope.PRICING)).thenReturn(pricingView);
        when(recommendationEngine.getRecommendationsForMappedProducts(pimView, category, pricingView)).thenReturn(List.of(recommendation));
        when(recommendation.toProduct()).thenReturn(product);
        when(recommendation.getInventoryKey()).thenReturn(key);

        // when
        List<ManagedCategoryProducts.Candidate> candidates = dynamicCategoryProducts.candidates(STORE_ID, category);

        // then
        assertThat(candidates).containsExactly(new ManagedCategoryProducts.Candidate(product, key));
    }

    @Test
    void aCategoryWithoutAPimMappingHasNoCandidatesAndReadsNoInventory() {
        // given
        CategoryDefinition unmapped = new CategoryDefinition().withGeneratedId();

        // when
        List<ManagedCategoryProducts.Candidate> candidates = dynamicCategoryProducts.candidates(STORE_ID, unmapped);

        // then
        assertThat(candidates).isEmpty();
        verifyNoInteractions(inventory, pimCatalog, recommendationEngine);
    }

    private static CategoryDefinition mappedTo(String pimCategoryId) {
        CategoryDefinition category = new CategoryDefinition().withGeneratedId();
        category.setPimCategoryIds(new LinkedList<>(List.of(pimCategoryId)));
        category.setType(CategoryDefinitionType.Dynamic);
        return category;
    }
}
