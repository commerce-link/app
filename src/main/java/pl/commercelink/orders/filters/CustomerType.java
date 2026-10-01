package pl.commercelink.orders.filters;

import pl.commercelink.orders.Order;

import java.util.Arrays;
import java.util.Optional;

/** Who an order is billed to: a company (a tax id in the billing details, {@link Order#isB2B()}) or a consumer. */
public enum CustomerType {

    B2B,
    B2C;

    boolean covers(Order order) {
        return (this == B2B) == order.isB2B();
    }

    public static Optional<CustomerType> parse(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst();
    }
}
