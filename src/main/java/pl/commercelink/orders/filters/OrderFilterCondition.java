package pl.commercelink.orders.filters;

import pl.commercelink.orders.Order;

import java.time.LocalDate;
import java.util.Optional;

public record OrderFilterCondition(OrderFilterField field, String value) {

    private static final char SEPARATOR = '=';

    public static Optional<OrderFilterCondition> of(OrderFilterField field, String rawValue) {
        String value = field.normalize(rawValue);
        return value.isEmpty() ? Optional.empty() : Optional.of(new OrderFilterCondition(field, value));
    }

    public static Optional<OrderFilterCondition> stored(String entry) {
        int separator = entry == null ? -1 : entry.indexOf(SEPARATOR);
        if (separator <= 0) {
            return Optional.empty();
        }
        return OrderFilterField.parse(entry.substring(0, separator))
                .map(field -> new OrderFilterCondition(field, entry.substring(separator + 1)));
    }

    public boolean matches(Order order, LocalDate today) {
        return field.matches(order, value, today);
    }

    public String canonical() {
        return field.name() + SEPARATOR + value;
    }
}
