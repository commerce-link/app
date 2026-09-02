package pl.commercelink.orders.filters;

import java.util.Arrays;
import java.util.Optional;

public enum ShippingDue {

    DueToday,
    Overdue,
    Unscheduled;

    public static Optional<ShippingDue> parse(String value) {
        return Arrays.stream(values())
                .filter(due -> due.name().equalsIgnoreCase(value))
                .findFirst();
    }
}
