package pl.commercelink.discounts;

import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.function.Function;

interface Discount {

    DiscountType getType();

    double getValue();

    DiscountScope getScope();

    double getMinOrderValue();

    boolean isActive();

    LocalDateTime getValidFrom();

    LocalDateTime getValidTo();

    default boolean covers(BasketItem item, Function<String, Set<String>> productAliases) {
        return getScope().matches(item, productAliases);
    }

    default BigDecimal minimum() {
        return money(getMinOrderValue());
    }

    default boolean isMinimumReachedBy(Basket basket) {
        return money(basket.getTotalPrice()).compareTo(minimum()) >= 0;
    }

    default DiscountCodeStatus statusAt(LocalDateTime now) {
        if (!isActive()) {
            return DiscountCodeStatus.Disabled;
        }
        if (getValidTo() != null && now.isAfter(getValidTo())) {
            return DiscountCodeStatus.Expired;
        }
        if (getValidFrom() != null && now.isBefore(getValidFrom())) {
            return DiscountCodeStatus.Scheduled;
        }
        return DiscountCodeStatus.Active;
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
