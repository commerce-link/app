package pl.commercelink.orders.filters;

import pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class OrderFilterConditions {

    private static final Comparator<OrderFilterCondition> BY_FIELD_THEN_VALUE =
            Comparator.comparing((OrderFilterCondition condition) -> condition.field().name())
                    .thenComparing(OrderFilterCondition::value);

    private final List<OrderFilterCondition> conditions;

    private OrderFilterConditions(List<OrderFilterCondition> conditions) {
        this.conditions = List.copyOf(conditions);
    }

    public static OrderFilterConditions of(List<OrderFilterCondition> conditions) {
        List<OrderFilterCondition> unique = conditions == null ? List.of() : new LinkedHashSet<>(conditions).stream()
                .filter(Objects::nonNull)
                .sorted(BY_FIELD_THEN_VALUE)
                .toList();

        if (unique.isEmpty()) {
            throw new OrderFilterInvalidException("orders.filters.error.no.conditions");
        }
        return new OrderFilterConditions(unique);
    }

    public List<OrderFilterCondition> conditions() {
        return conditions;
    }

    public boolean matchedBy(java.util.function.Predicate<OrderFilterCondition> predicate) {
        return conditions.stream().allMatch(predicate);
    }
}
