package pl.commercelink.products;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.stores.SupplierScope;

import java.util.List;

/**
 * The products an automatic category puts on the price list, as candidates for the same category turned manual: the
 * recommendations with a PIM entry and a listed label, read from the inventory the price list is generated from
 * (PricelistEventListener), so the manual category starts with the products it priced a moment ago.
 */
@Component
@RequiredArgsConstructor
public class DynamicCategoryProducts {

    private final ProductRecommendationEngine recommendationEngine;
    private final PimCatalog pimCatalog;
    private final Inventory inventory;

    public List<ManagedCategoryProducts.Candidate> candidates(String storeId, CategoryDefinition category) {
        // Without PIM categories the engine has nothing to match, and reading the inventory would be wasted work.
        if (!category.hasCategoryMapping()) {
            return List.of();
        }
        return recommendationEngine.getRecommendationsForMappedProducts(pimCatalog.withStoreData(storeId), category,
                        inventory.withEnabledSuppliersAndWarehouseData(storeId, SupplierScope.PRICING))
                .stream()
                .map(recommendation -> new ManagedCategoryProducts.Candidate(recommendation.toProduct(), recommendation.getInventoryKey()))
                .toList();
    }
}
