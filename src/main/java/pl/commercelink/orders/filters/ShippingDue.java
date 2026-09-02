package pl.commercelink.orders.filters;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

public enum ShippingDue {

    DueToday {
        @Override
        boolean covers(LocalDate estimatedShippingAt, LocalDate today) {
            return estimatedShippingAt != null && !estimatedShippingAt.isAfter(today);
        }
    },

    Overdue {
        @Override
        boolean covers(LocalDate estimatedShippingAt, LocalDate today) {
            return estimatedShippingAt != null && estimatedShippingAt.isBefore(today);
        }
    },

    Unscheduled {
        @Override
        boolean covers(LocalDate estimatedShippingAt, LocalDate today) {
            return estimatedShippingAt == null;
        }
    };

    abstract boolean covers(LocalDate estimatedShippingAt, LocalDate today);

    public static Optional<ShippingDue> parse(String value) {
        return Arrays.stream(values())
                .filter(due -> due.name().equalsIgnoreCase(value))
                .findFirst();
    }
}
