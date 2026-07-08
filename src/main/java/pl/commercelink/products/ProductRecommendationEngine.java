package pl.commercelink.products;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ProductRecommendationEngine {

    private final PimCatalog pimCatalog;
    private final ProductRepository productRepository;

    ProductRecommendationEngine(PimCatalog pimCatalog, ProductRepository productRepository) {
        this.pimCatalog = pimCatalog;
        this.productRepository = productRepository;
    }

    /**
     * That method should operate only on objects managed in memory without access to time-consuming PIM repository. We should consider including
     * recommendations for products that are not currently available via PimIndex.
     */
    public List<ProductRecommendation> getRecommendations(CategoryDefinition categoryDefinition, InventoryView inventory) {
        List<InventoryKey> alreadyMappedInventoryKeys = productRepository.findAll(categoryDefinition.getCategoryId())
                .stream()
                .map(InventoryKey::fromProduct)
                .collect(Collectors.toList());

        return inventory.findAllByProductCategory(categoryDefinition.getCategory())
                .stream()
                .filter(MatchedInventory::hasAnyOffers)
                .filter(i -> isNotMapped(i.getInventoryKey(), alreadyMappedInventoryKeys))
                .filter(i -> matchesAllDefinitions(i, categoryDefinition.getInventoryDefinitions()))
                .map(i -> createRecommendation(categoryDefinition, i))
                .filter(r -> StringUtils.isNotBlank(r.getBrand()))
                .sorted(Comparator.comparing(ProductRecommendation::getBrand).thenComparingDouble(ProductRecommendation::getLowestGrossPrice))
                .collect(Collectors.toList());
    }

    public List<ProductRecommendation> getRecommendationsForMappedProducts(CategoryDefinition categoryDefinition, InventoryView inventory) {
        return getRecommendations(categoryDefinition, inventory).stream()
                .filter(ProductRecommendation::hasPimId)
                .filter(r -> matchesGroupingOrder(r, categoryDefinition))
                .collect(Collectors.toList());
    }

    public List<ProductRecommendation> getRecommendationsForUnmappedProducts(CategoryDefinition categoryDefinition, InventoryView inventory) {
        return getRecommendations(categoryDefinition, inventory).stream()
                .filter(ProductRecommendation::hasPimId)
                .filter(r -> !matchesGroupingOrder(r, categoryDefinition))
                .collect(Collectors.toList());
    }

    private boolean matchesGroupingOrder(ProductRecommendation recommendation, CategoryDefinition categoryDefinition) {
        if (categoryDefinition.hasGrouping()) {
            return categoryDefinition.getGroupingOrder().contains(recommendation.getLabel());
        }
        return true;
    }

    private ProductRecommendation createRecommendation(CategoryDefinition categoryDefinition, MatchedInventory matchedInventory) {
        InventoryKey key = matchedInventory.getInventoryKey();
        Optional<PimEntry> pimEntry = pimCatalog.findByPimIdOrGtinsOrMpns(key.getId(), key.getProductEans(), key.getProductCodes());
        return new ProductRecommendation(categoryDefinition, matchedInventory, pimEntry);
    }

    private boolean matchesAllDefinitions(MatchedInventory matchedInventory, List<InventoryDefinition> inventoryDefinitions) {
        return inventoryDefinitions.stream().allMatch(def -> def.test(matchedInventory));
    }

    private boolean isNotMapped(InventoryKey inventoryKey, List<InventoryKey> alreadyMappedProducts) {
        return alreadyMappedProducts.stream().noneMatch(i -> i.matches(inventoryKey));
    }
}
