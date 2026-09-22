package pl.commercelink.orders.filters;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

public enum ShippingDue {

    DueToday,
    Overdue,
    Unscheduled;

    boolean covers(LocalDate shippingDueAt, LocalDate today) {
        return switch (this) {
            case DueToday -> shippingDueAt != null && !shippingDueAt.isAfter(today);
            case Overdue -> shippingDueAt != null && shippingDueAt.isBefore(today);
            case Unscheduled -> shippingDueAt == null;
        };
    }

    public static Optional<ShippingDue> parse(String value) {
        return Arrays.stream(values())
                .filter(due -> due.name().equalsIgnoreCase(value))
                .findFirst();
    }
}
