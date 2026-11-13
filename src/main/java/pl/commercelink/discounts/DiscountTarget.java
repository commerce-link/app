package pl.commercelink.discounts;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.baskets.BasketItem;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public enum DiscountTarget {
    Product,
    Category,
    Brand;

    public String valueIn(BasketItem item) {
        return switch (this) {
            case Product -> item.getId();
            case Category -> item.getCategory();
            case Brand -> item.getBrand();
        };
    }

    /**
     * @param productAliases the ids a product is known by in the store (itself, its source, its clone); a product
     *                       target matches the item when any of them is among the values
     */
    boolean matches(BasketItem item, List<TargetValue> values, Function<String, Set<String>> productAliases) {
        if (!item.isProduct()) {
            return false;
        }
        Set<String> ids = new HashSet<>();
        ids.add(StringUtils.trim(valueIn(item)));
        if (this == Product && item.getId() != null) {
            productAliases.apply(item.getId()).forEach(alias -> ids.add(StringUtils.trim(alias)));
        }
        ids.remove(null);
        return values.stream().anyMatch(v -> ids.contains(StringUtils.trim(v.getId())));
    }
}
