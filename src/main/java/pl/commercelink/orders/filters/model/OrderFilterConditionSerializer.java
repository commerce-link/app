package pl.commercelink.orders.filters.model;

import pl.commercelink.orders.filters.OrderFilterCondition;
import pl.commercelink.orders.filters.OrderFilterConditions;
import pl.commercelink.orders.filters.OrderFilterField;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class OrderFilterConditionSerializer {

    private static final char SEPARATOR = '=';

    private OrderFilterConditionSerializer() {
    }

    public static List<String> toStoredEntries(OrderFilterConditions conditions) {
        return conditions.conditions().stream()
                .map(OrderFilterConditionSerializer::toStoredEntry)
                .toList();
    }

    public static Optional<OrderFilterConditions> fromStoredEntries(List<String> storedEntries) {
        if (storedEntries == null || storedEntries.isEmpty()) {
            return Optional.empty();
        }

        List<OrderFilterCondition> conditions = new ArrayList<>();
        for (String entry : storedEntries) {
            Optional<OrderFilterCondition> condition = fromStoredEntry(entry);
            if (condition.isEmpty()) {
                return Optional.empty();
            }
            conditions.add(condition.get());
        }
        return Optional.of(OrderFilterConditions.of(conditions));
    }

    private static String toStoredEntry(OrderFilterCondition condition) {
        return condition.field().name() + SEPARATOR + condition.value();
    }

    private static Optional<OrderFilterCondition> fromStoredEntry(String entry) {
        int separator = entry == null ? -1 : entry.indexOf(SEPARATOR);
        if (separator <= 0) {
            return Optional.empty();
        }
        return OrderFilterField.parse(entry.substring(0, separator))
                .map(field -> new OrderFilterCondition(field, entry.substring(separator + 1)));
    }
}
