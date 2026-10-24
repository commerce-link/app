package pl.commercelink.discounts;

import pl.commercelink.baskets.BasketItem;

public record DiscountedLine(BasketItem item, double unitPrice) {
}
