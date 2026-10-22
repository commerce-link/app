package pl.commercelink.products;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.MatchedInventory;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimView;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ProductRecommendationEngine {

    private final ProductRepository productRepository;

    ProductRecommendationEngine(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * That method should operate only on objects managed in memory without access to time-consuming PIM repository. We should consider including
     * recommendations for products that are not currently available via PimIndex.
     */
    public List<ProductRecommendation> getRecommendations(PimView pim, CategoryDefinition categoryDefinition, InventoryView inventory) {
        if (!categoryDefinition.hasCategoryMapping()) {
            return List.of();
        }

        List<InventoryKey> alreadyMappedInventoryKeys = productRepository.findAll(categoryDefinition.getCategoryId())
                .stream()
                .map(InventoryKey::fromProduct)
                .collect(Collectors.toList());

        return inventory.findAllByProductCategoryIds(categoryDefinition.getPimCategoryIds())
                .values()
                .stream()
                .flatMap(Collection::stream)
                .filter(MatchedInventory::hasAnyOffers)
                .filter(i -> isNotMapped(i.getInventoryKey(), alreadyMappedInventoryKeys))
                .filter(i -> matchesAllDefinitions(i, categoryDefinition.getInventoryDefinitions()))
                .map(i -> createRecommendation(pim, categoryDefinition, i))
                .filter(r -> StringUtils.isNotBlank(r.getBrand()))
                .sorted(Comparator.comparing(ProductRecommendation::getBrand).thenComparingDouble(ProductRecommendation::getLowestGrossPrice))
                .filter(oncePerStoreEntry())
                .collect(Collectors.toList());
    }

    // a store entry shadowing two global entries pulls both of their inventory groups onto one pim id: list it once,
    // at its lowest price, which the sort above puts first. Global entries are left as they were: two price groups of
    // one global entry (InventoryAutoDiscovery splits suspicious prices) stay two rows, as before stage 2
    private static Predicate<ProductRecommendation> oncePerStoreEntry() {
        Set<String> seen = new HashSet<>();
        return r -> !r.hasStoreEntry() || seen.add(r.getPimId());
    }

    public List<ProductRecommendation> getRecommendationsForMappedProducts(PimView pim, CategoryDefinition categoryDefinition, InventoryView inventory) {
        return getRecommendations(pim, categoryDefinition, inventory).stream()
                .filter(ProductRecommendation::hasPimId)
                .filter(r -> matchesGroupingOrder(r, categoryDefinition))
                .collect(Collectors.toList());
    }

    public List<ProductRecommendation> getRecommendationsForUnmappedProducts(PimView pim, CategoryDefinition categoryDefinition, InventoryView inventory) {
        return getRecommendations(pim, categoryDefinition, inventory).stream()
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

    private ProductRecommendation createRecommendation(PimView pim, CategoryDefinition categoryDefinition, MatchedInventory matchedInventory) {
        InventoryKey key = matchedInventory.getInventoryKey();
        Optional<PimEntry> pimEntry = pim.findByPimIdOrGtinsOrMpns(key.getId(), key.getProductEans(), key.getProductCodes());
        return new ProductRecommendation(categoryDefinition, matchedInventory, pimEntry);
    }

    private boolean matchesAllDefinitions(MatchedInventory matchedInventory, List<InventoryDefinition> inventoryDefinitions) {
        return inventoryDefinitions.stream().allMatch(def -> def.test(matchedInventory));
    }

    private boolean isNotMapped(InventoryKey inventoryKey, List<InventoryKey> alreadyMappedProducts) {
        return alreadyMappedProducts.stream().noneMatch(i -> i.matches(inventoryKey));
    }
}
