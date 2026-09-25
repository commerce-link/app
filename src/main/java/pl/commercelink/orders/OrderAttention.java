package pl.commercelink.orders;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

/**
 * The four "needs attention" figures of the orders list (spec §4). Each is a predicate over an order, so the tiles
 * and the ?focus= narrowing of the list use the same definition.
 */
public enum OrderAttention {
    Overdue("overdue") {
        @Override
        public boolean matches(Order order, LocalDate today) {
            LocalDate due = order.getShippingDueAt();
            return due != null && due.isBefore(today) && isBeforeShipping(order);
        }
    },
    Today("today") {
        @Override
        public boolean matches(Order order, LocalDate today) {
            return today.equals(order.getShippingDueAt()) && isBeforeShipping(order);
        }
    },
    Decide("decide") {
        @Override
        public boolean matches(Order order, LocalDate today) {
            return order.hasOneOfStatuses(OrderStatus.New, OrderStatus.Blocked);
        }
    },
    Unpaid("unpaid") {
        @Override
        public boolean matches(Order order, LocalDate today) {
            return isOpen(order) && order.getUnpaidAmount() > 0;
        }
    },
    /** Placed today — the day's intake, a pulse figure rather than a queue. */
    NewToday("newToday") {
        @Override
        public boolean matches(Order order, LocalDate today) {
            return order.getOrderedAt() != null && today.equals(order.getOrderedAt().toLocalDate())
                    && order.getStatus() != OrderStatus.Cancelled;
        }
    };

    private final String param;

    OrderAttention(String param) {
        this.param = param;
    }

    public String param() {
        return param;
    }

    public abstract boolean matches(Order order, LocalDate today);

    public static Optional<OrderAttention> parse(String value) {
        return Arrays.stream(values()).filter(kind -> kind.param.equalsIgnoreCase(value == null ? "" : value.trim())).findFirst();
    }

    /** Not Completed and not Cancelled — the same definition as OrdersRepository.findAllActiveOrders. */
    public static boolean isOpen(Order order) {
        return order.getStatus() != null && !order.hasOneOfStatuses(OrderStatus.Completed, OrderStatus.Cancelled);
    }

    /** Still in the store's hands: the shipping date can still be met or missed. */
    public static boolean isBeforeShipping(Order order) {
        return order.hasOneOfStatuses(OrderStatus.New, OrderStatus.Blocked, OrderStatus.Assembly,
                OrderStatus.Assembled, OrderStatus.Realization);
    }
}
