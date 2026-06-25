package pl.commercelink.taxonomy;

import java.util.Arrays;

public class CategoryTreeCache {

    public int sequenceNumberOf(String categoryKey) {
        if (categoryKey == null) {
            return Integer.MAX_VALUE;
        }
        return Arrays.stream(ProductCategory.values())
                .filter(category -> category.name().equals(categoryKey))
                .findFirst()
                .map(Enum::ordinal)
                .orElse(Integer.MAX_VALUE);
    }

    public String groupForCategoryKey(String categoryKey) {
        if (categoryKey == null) {
            return null;
        }
        return Arrays.stream(ProductCategory.values())
                .filter(category -> category.name().equals(categoryKey))
                .findFirst()
                .map(category -> category.getProductGroup().name())
                .orElse(null);
    }
}
