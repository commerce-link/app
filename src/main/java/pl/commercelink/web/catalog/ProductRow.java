package pl.commercelink.web.catalog;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductRecommendation;
import pl.commercelink.web.dtos.FormNumbers;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * One row of the products table. {@code searchText} is what the browser-side search matches (lower-cased), and
 * {@code features} are the marks the "Show" filter offers. A row of an automatic category is computed from the
 * inventory: it has a price but no page of its own and nothing to select, so {@code href} is null.
 */
public record ProductRow(String id, String name, String ean, String mfn, String pimId, String brand, String label,
                         boolean labelOutside, String pricingGroup, List<String> marketplaceNames, ProductStatus status,
                         Set<String> features, String searchText, String href, String lowestGrossPrice) {

    public static ProductRow of(Product product, CategoryDefinition category, String catalogId,
                                Function<String, String> marketplaceDisplayName) {
        Set<String> features = new LinkedHashSet<>();
        if (!product.getMarketplaces().isEmpty()) {
            features.add("marketplace");
        }
        if (product.getStockExpectedQty() > 0) {
            features.add("stock");
        }
        if (product.getSuggestedRetailPrice() > 0) {
            features.add("srp");
        }
        if (product.getMaxRetailPrice() > 0) {
            features.add("mrp");
        }
        if (product.isService()) {
            features.add("service");
        }
        return new ProductRow(product.getProductId(), product.getName(), product.getEan(), product.getManufacturerCode(),
                product.getPimId(), product.getBrand(), product.getLabel(), outside(category, product.getLabel()),
                product.getPricingGroup(),
                product.getMarketplaces().stream().map(marketplaceDisplayName).toList(),
                ProductStatus.of(product), features,
                search(product.getName(), product.getEan(), product.getManufacturerCode(), product.getPimId(),
                        product.getBrand(), product.getLabel()),
                CatalogPaths.product(catalogId, category.getCategoryId(), product.getProductId()), null);
    }

    public static ProductRow ofRecommendation(ProductRecommendation recommendation, CategoryDefinition category) {
        return new ProductRow(recommendation.getEan(), recommendation.getName(), recommendation.getEan(),
                recommendation.getManufacturerCode(), recommendation.getPimId(), recommendation.getBrand(),
                recommendation.getLabel(), outside(category, recommendation.getLabel()), null, List.of(),
                ProductStatus.ofRecommendation(recommendation, category), Set.of(),
                search(recommendation.getName(), recommendation.getEan(), recommendation.getManufacturerCode(),
                        recommendation.getPimId(), recommendation.getBrand(), recommendation.getLabel()),
                null, FormNumbers.format(recommendation.getLowestGrossPrice()));
    }

    /** The value of the row's feature attribute; the filter group is declared multi-valued, so it splits on spaces. */
    public String featuresAttribute() {
        return String.join(" ", features);
    }

    private static boolean outside(CategoryDefinition category, String label) {
        return category.hasGrouping() && !category.getGroupingOrder().contains(label);
    }

    private static String search(String... parts) {
        return Stream.of(parts)
                .filter(StringUtils::isNotBlank)
                .map(String::toLowerCase)
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }
}
