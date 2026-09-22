package pl.commercelink.products;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Saves one section of a category definition at a time (basics, pricing, one marketplace, filters) so the four settings
 * pages never overwrite each other. Sanitising rules match ProductCatalog.addOrUpdateCategoryDefinition: blank labels
 * and PIM ids are dropped, incomplete price groups/marketplaces/filters are rejected by the forms before they get here.
 */
@Service
@RequiredArgsConstructor
public class CategoryDefinitions {

    public record Basics(String name, int sequenceNumber, List<String> pimCategoryIds, CategoryDefinitionType type, int maxQty,
                         boolean requiredDuringOrder, boolean includedInDeliverySuggestions, boolean deletionProtection,
                         List<String> labels) {
    }

    public record RemoveResult(boolean productsKept, int productsDeleted) {
    }

    /** What remove() will do, for the confirmation page: the same decision, made in the same place, without deleting. */
    public record DeletionPreview(boolean productsKept, int productsToDelete) {
    }

    private final ProductCatalogRepository catalogs;
    private final ProductRepository products;

    public CategoryDefinition create(ProductCatalog catalog, Basics basics) {
        CategoryDefinition category = new CategoryDefinition()
                .withGeneratedId()
                .withSequenceNumber(basics.sequenceNumber() > 0 ? basics.sequenceNumber() : catalog.getNextSequenceNumber())
                .withStockDefinition(new StockDefinition(1, 10, 30))
                .withAvailabilityDefinition(new AvailabilityDefinition(3, 1))
                .withPriceDefinition(new PriceDefinition(1.00, 0, 0, 0, 0, PriceDefinition.DEFAULT_PRICING_GROUP));
        applyBasics(category, basics);
        catalog.getCategories().add(category);
        sortAndSave(catalog);
        return category;
    }

    public void saveBasics(ProductCatalog catalog, CategoryDefinition category, Basics basics) {
        if (category.getType() != basics.type()) {
            category.setTypeChangedAt(LocalDateTime.now());
        }
        applyBasics(category, basics);
        if (basics.sequenceNumber() > 0) {
            category.setSequenceNumber(basics.sequenceNumber());
        }
        sortAndSave(catalog);
    }

    private void applyBasics(CategoryDefinition category, Basics basics) {
        category.setName(StringUtils.trimToNull(basics.name()));
        category.setPimCategoryIds(distinctNonBlank(basics.pimCategoryIds()));
        category.setType(basics.type());
        category.setMaxQty(basics.maxQty());
        category.setRequiredDuringOrder(basics.requiredDuringOrder());
        category.setIncludedInDeliverySuggestions(basics.includedInDeliverySuggestions());
        category.setDeletionProtection(basics.deletionProtection());
        category.setGroupingOrder(distinctNonBlank(basics.labels()));
    }

    public void savePricing(ProductCatalog catalog, CategoryDefinition category, StockDefinition stock,
                            AvailabilityDefinition availability, List<PriceDefinition> groups) {
        category.setStockDefinition(stock);
        category.setAvailabilityDefinition(availability);
        category.setPriceDefinitions(new ArrayList<>(groups));
        sortAndSave(catalog);
    }

    public void saveMarketplace(ProductCatalog catalog, CategoryDefinition category, MarketplaceDefinition definition) {
        category.getMarketplaceDefinitions().removeIf(m -> Objects.equals(m.getName(), definition.getName()));
        category.getMarketplaceDefinitions().add(definition);
        sortAndSave(catalog);
    }

    public void removeMarketplace(ProductCatalog catalog, CategoryDefinition category, String name) {
        category.getMarketplaceDefinitions().removeIf(m -> Objects.equals(m.getName(), name));
        sortAndSave(catalog);
    }

    public void saveFilters(ProductCatalog catalog, CategoryDefinition category, List<InventoryDefinition> filters) {
        category.setInventoryDefinitions(new LinkedList<>(filters));
        sortAndSave(catalog);
    }

    public DeletionPreview deletionPreview(ProductCatalog catalog, CategoryDefinition category) {
        boolean kept = productsSurviveRemoval(catalog, category);
        return new DeletionPreview(kept, kept ? 0 : products.findAll(category.getCategoryId()).size());
    }

    public RemoveResult remove(ProductCatalog catalog, CategoryDefinition category) {
        if (category.isDeletionProtection()) {
            throw new IllegalStateException("Category " + category.getCategoryId() + " is protected from deletion");
        }
        boolean kept = productsSurviveRemoval(catalog, category);
        catalog.getCategories().remove(category);
        int deleted = 0;
        if (!kept) {
            List<Product> orphaned = products.findAll(category.getCategoryId());
            if (!orphaned.isEmpty()) {
                products.delete(orphaned);
            }
            deleted = orphaned.size();
        }
        catalogs.save(catalog);
        return new RemoveResult(kept, deleted);
    }

    /** The products outlive the category when another category of the catalog is mapped to one of its PIM categories. */
    private boolean productsSurviveRemoval(ProductCatalog catalog, CategoryDefinition category) {
        return category.hasCategoryMapping() && catalog.getCategories().stream()
                .filter(other -> other != category)
                .anyMatch(other -> other.getPimCategoryIds().stream().anyMatch(category.getPimCategoryIds()::contains));
    }

    public boolean priceGroupInUse(CategoryDefinition category, String group) {
        return productsInPriceGroup(category, group) > 0;
    }

    public int productsInPriceGroup(CategoryDefinition category, String group) {
        return (int) products.findAll(category.getCategoryId()).stream()
                .filter(p -> group.equalsIgnoreCase(p.getPricingGroup()))
                .count();
    }

    private void sortAndSave(ProductCatalog catalog) {
        catalog.getCategories().sort(Comparator.comparingInt(CategoryDefinition::getSequenceNumber));
        catalogs.save(catalog);
    }

    private static List<String> distinctNonBlank(List<String> values) {
        return values == null ? new LinkedList<>() : values.stream()
                .map(StringUtils::trim).filter(StringUtils::isNotEmpty).distinct()
                .collect(Collectors.toCollection(LinkedList::new));
    }
}
