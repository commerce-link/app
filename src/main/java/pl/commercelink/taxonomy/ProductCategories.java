package pl.commercelink.taxonomy;

import java.util.Optional;

public final class ProductCategories {

    private ProductCategories() {
    }

    public static Optional<ProductCategory> tryParse(String categoryKey) {
        if (categoryKey == null) return Optional.empty();
        try {
            return Optional.of(ProductCategory.valueOf(categoryKey));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
