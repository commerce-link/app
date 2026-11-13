package pl.commercelink.discounts;

import pl.commercelink.baskets.BasketItem;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

interface Discount {

    DiscountType getType();

    double getValue();

    DiscountTarget getTarget();

    List<TargetValue> getTargetValues();

    boolean isActive();

    LocalDateTime getValidFrom();

    LocalDateTime getValidTo();

    default boolean covers(BasketItem item, Function<String, Set<String>> productAliases) {
        DiscountTarget target = getTarget();
        return target == null ? item.isProduct() : target.matches(item, getTargetValues(), productAliases);
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
}
