package pl.commercelink.web.catalog;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.products.ProductRecommendation;
import pl.commercelink.web.dtos.FormNumbers;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * One product of the inventory proposed for a category: what it is, what it costs and who sells it.
 * {@code alternatives} are the other identifiers the suppliers use for the same product, {@code searchText} is what
 * the browser-side search matches (lower-cased) and {@code lowestGrossPrice} is the formatted amount without a
 * currency, so the column sorts as a number and the template appends the currency.
 */
public record RecommendationRow(String ean, String name, String brand, String mfn, boolean hasPim,
                                String lowestGrossPrice, List<String> suppliers, String alternatives,
                                String searchText, String addHref) {

    /** A product the inventory knows without a brand; a filter value must not be empty, or the row filters nothing. */
    public static final String NO_BRAND = "—";

    public static RecommendationRow of(ProductRecommendation recommendation, String catalogId, String categoryId,
                                       Function<String, String> supplierLabel) {
        List<String> suppliers = recommendation.getAlternativeSuppliers().stream()
                .map(supplierLabel)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        String alternatives = Stream.concat(
                        recommendation.getAlternativeEans().stream().filter(ean -> !ean.equals(recommendation.getEan())),
                        recommendation.getAlternativeProductCodes().stream()
                                .filter(code -> !code.equals(recommendation.getManufacturerCode())))
                .filter(StringUtils::isNotBlank)
                .distinct()
                .reduce((one, other) -> one + " · " + other)
                .orElse("");
        return new RecommendationRow(recommendation.getEan(), recommendation.getName(),
                StringUtils.defaultIfBlank(recommendation.getBrand(), NO_BRAND), recommendation.getManufacturerCode(),
                recommendation.hasPimId(), FormNumbers.format(recommendation.getLowestGrossPrice()), suppliers,
                alternatives,
                search(recommendation.getName(), recommendation.getEan(), recommendation.getManufacturerCode(),
                        recommendation.getBrand(), alternatives),
                CatalogPaths.newProduct(catalogId, categoryId) + "?ean=" + recommendation.getEan());
    }

    private static String search(String... parts) {
        return Stream.of(parts)
                .filter(StringUtils::isNotBlank)
                .map(String::toLowerCase)
                .reduce((one, other) -> one + " " + other)
                .orElse("");
    }
}
