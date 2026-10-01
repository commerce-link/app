package pl.commercelink.web.catalog;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductRecommendation;

/**
 * Status column and filter of the category page, replacing the seven product "views" of the old category menu. A manual
 * product is active when it is enabled and known to the PIM; a product of an automatic category has nothing to enable,
 * so there the status says whether its label is still on the category's label list (a label outside it drops the
 * product from the price list).
 */
public enum ProductStatus {

    ACTIVE("active", "is-ok"),
    DISABLED("disabled", "is-neutral"),
    NO_PIM("nopim", "is-warn");

    private final String filter;
    private final String tone;

    ProductStatus(String filter, String tone) {
        this.filter = filter;
        this.tone = tone;
    }

    public String filter() {
        return filter;
    }

    public String tone() {
        return tone;
    }

    /** The name of the filter ("Wyłączone"), which is about a group of products. */
    public String labelKey() {
        return "catalog.products.status." + filter;
    }

    /** The name in the status pill of one row ("Wyłączony"). */
    public String pillKey() {
        return "catalog.products.pill." + filter;
    }

    public static ProductStatus of(Product product) {
        if (!product.isEnabled()) {
            return DISABLED;
        }
        return StringUtils.isBlank(product.getPimId()) ? NO_PIM : ACTIVE;
    }

    public static ProductStatus ofRecommendation(ProductRecommendation recommendation, CategoryDefinition category) {
        if (!recommendation.hasPimId()) {
            return NO_PIM;
        }
        boolean inList = !category.hasGrouping() || category.getGroupingOrder().contains(recommendation.getLabel());
        return inList ? ACTIVE : DISABLED;
    }

    /** The status a filter value stands for, or null when the value is not one of them ("all", or a stale address). */
    public static ProductStatus fromFilter(String filter) {
        for (ProductStatus status : values()) {
            if (status.filter.equals(filter)) {
                return status;
            }
        }
        return null;
    }
}
