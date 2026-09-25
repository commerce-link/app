package pl.commercelink.web.catalog;

import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.CategoryDefinitions;
import pl.commercelink.products.MarketplaceDefinition;
import pl.commercelink.products.ProductCatalog;

import java.util.List;
import java.util.function.Function;

/**
 * One category on the catalog page, built from the labels of its products (one entry per product) rather than the
 * products. productsCount is null for an automatic category (its list is computed on demand);
 * productsKept and productsToDelete are what removing the category would do, so the dialog says the same as the
 * confirmation page behind it.
 */
public record CategoryRow(String id, String name, boolean dynamic, List<String> pimCategoryNames, Integer productsCount,
                          boolean productsKept, int productsToDelete, int labelsCount, int productsOutsideLabels,
                          List<String> marketplaceNames, int maxQty, boolean required, boolean deletable, String href,
                          String settingsHref, String deleteHref) {

    public static CategoryRow of(ProductCatalog catalog, CategoryDefinition category, List<String> pimNames,
                                 List<String> productLabels, Function<String, String> marketplaceDisplayName,
                                 CategoryDefinitions.DeletionPreview deletion) {
        boolean dynamic = category.hasType(CategoryDefinitionType.Dynamic);
        // Products, not labels: a product whose label is not on the list falls out of the price list, and two of them
        // can share one unlisted label. One entry per product, so a product without a label counts as well (null is
        // never on the list, and an immutable list refuses to be asked about it).
        int outside = category.hasGrouping()
                ? (int) productLabels.stream().filter(label -> label == null || !category.getGroupingOrder().contains(label)).count()
                : 0;
        List<String> marketplaces = category.getMarketplaceDefinitions().stream()
                .filter(m -> m.isEnabled() && m.isComplete())
                .map(MarketplaceDefinition::getName)
                .map(marketplaceDisplayName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        String catalogId = catalog.getCatalogId();
        String categoryId = category.getCategoryId();
        return new CategoryRow(categoryId, category.getName(), dynamic, pimNames, dynamic ? null : productLabels.size(),
                deletion.productsKept(), deletion.productsToDelete(),
                category.getGroupingOrder().size(), outside, marketplaces, category.getMaxQty(),
                category.isRequiredDuringOrder(), !category.isDeletionProtection(),
                CatalogPaths.category(catalogId, categoryId), CatalogPaths.categorySettings(catalogId, categoryId),
                CatalogPaths.categoryDelete(catalogId, categoryId));
    }

    public boolean hasPimMapping() {
        return !pimCategoryNames.isEmpty();
    }
}
