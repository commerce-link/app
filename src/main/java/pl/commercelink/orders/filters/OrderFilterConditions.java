package pl.commercelink.orders.filters;

import pl.commercelink.orders.filters.exceptions.OrderFilterInvalidException;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

public final class OrderFilterConditions {

    private final List<String> entries;

    private OrderFilterConditions(List<String> entries) {
        this.entries = List.copyOf(entries);
    }

    public static OrderFilterConditions of(List<OrderFilterCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            throw new OrderFilterInvalidException("A filter needs at least one condition");
        }

        TreeSet<String> canonical = new TreeSet<>();
        conditions.stream()
                .filter(java.util.Objects::nonNull)
                .map(OrderFilterCondition::canonical)
                .forEach(canonical::add);

        if (canonical.isEmpty()) {
            throw new OrderFilterInvalidException("A filter needs at least one condition");
        }
        return new OrderFilterConditions(List.copyOf(canonical));
    }

    public static OrderFilterConditions stored(List<String> storedEntries) {
        return new OrderFilterConditions(storedEntries == null ? List.of() : storedEntries);
    }

    public List<String> entries() {
        return entries;
    }

    public List<OrderFilterCondition> conditions() {
        return entries.stream()
                .map(OrderFilterCondition::stored)
                .flatMap(Optional::stream)
                .toList();
    }

    public boolean isReadable() {
        return !entries.isEmpty() && conditions().size() == entries.size();
    }
}
