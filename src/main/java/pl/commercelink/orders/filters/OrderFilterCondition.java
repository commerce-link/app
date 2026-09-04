package pl.commercelink.orders.filters;

import pl.commercelink.orders.Order;

import java.time.LocalDate;

public record OrderFilterCondition(OrderFilterField field, String value) {

    public boolean matches(Order order, LocalDate today) {
        return field.matches(order, value, today);
    }
}
