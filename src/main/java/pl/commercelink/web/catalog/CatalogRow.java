package pl.commercelink.web.catalog;

import pl.commercelink.products.CategoryDefinitionType;
import pl.commercelink.products.ProductCatalog;

/** One catalog on the list: what the operator needs to know before opening it. */
public record CatalogRow(String id, String name, int categories, int managed, int dynamic, String scheduleText,
                         boolean protectedFromDeletion, String href, String settingsHref) {

    public static CatalogRow of(ProductCatalog catalog, String scheduleText) {
        int managed = (int) catalog.getCategories().stream().filter(c -> c.hasType(CategoryDefinitionType.Managed)).count();
        return new CatalogRow(catalog.getCatalogId(), catalog.getName(), catalog.getCategories().size(), managed,
                catalog.getCategories().size() - managed, scheduleText, catalog.isDeletionProtection(),
                CatalogPaths.catalog(catalog.getCatalogId()), CatalogPaths.catalogSettings(catalog.getCatalogId()));
    }
}
