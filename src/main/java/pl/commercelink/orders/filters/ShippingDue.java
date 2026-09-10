package pl.commercelink.orders.filters;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

public enum ShippingDue {

    DueToday,
    Overdue,
    Unscheduled;

    boolean covers(LocalDate estimatedShippingAt, LocalDate today) {
        return switch (this) {
            case DueToday -> estimatedShippingAt != null && !estimatedShippingAt.isAfter(today);
            case Overdue -> estimatedShippingAt != null && estimatedShippingAt.isBefore(today);
            case Unscheduled -> estimatedShippingAt == null;
        };
    }

    public static Optional<ShippingDue> parse(String value) {
        return Arrays.stream(values())
                .filter(due -> due.name().equalsIgnoreCase(value))
                .findFirst();
    }
}
