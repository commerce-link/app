package pl.commercelink.discounts;

import pl.commercelink.baskets.BasketItem;

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
}
