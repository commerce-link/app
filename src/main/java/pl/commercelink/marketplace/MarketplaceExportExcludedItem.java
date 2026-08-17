package pl.commercelink.marketplace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketplaceExportExcludedItem(
        String reason,
        String categoryId,
        String categoryName,
        String productId,
        String pimId,
        String ean,
        String name,
        MarketplaceExportThresholds detail) {

    public static MarketplaceExportExcludedItem category(CategoryDefinition category,
                                                        MarketplaceExportSkipReason reason) {
        return new MarketplaceExportExcludedItem(
                reason.name(), category.getCategoryId(), category.getName(), null, null, null, null, null);
    }

    public static MarketplaceExportExcludedItem product(CategoryDefinition category,
                                                       Product product,
                                                       MarketplaceExportSkipReason reason,
                                                       MarketplaceExportThresholds detail) {
        return new MarketplaceExportExcludedItem(
                reason.name(),
                category.getCategoryId(),
                category.getName(),
                product.getProductId(),
                product.getPimId(),
                product.getEan(),
                product.getName(),
                detail);
    }
}
