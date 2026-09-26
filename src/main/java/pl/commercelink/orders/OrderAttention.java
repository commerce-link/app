package pl.commercelink.orders;

import java.time.LocalDate;

/**
 * The read-only figures above the orders list (spec §15, §17): Po terminie, Na dziś, Nieopłacone, Nowe dziś. Each is a
 * predicate over an order, counted over all open orders of the store; the tiles are in declaration order.
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
