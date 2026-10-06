package pl.commercelink.orders.history;

import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderStatus;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/** An order item of this store that carries the serial number, with its order. */
public record OrderLine(Order order, OrderItem item) {

    private static final Set<FulfilmentStatus> LEFT_THE_ORDER =
            EnumSet.of(FulfilmentStatus.Returned, FulfilmentStatus.Replaced, FulfilmentStatus.Destroyed);

    public LocalDateTime placedAt() {
        return order.getOrderedAt() != null ? order.getOrderedAt() : order.getLastEventDate();
    }

    /** The unit is (or was last) with this order: not returned, replaced or destroyed, and the order not cancelled. */
    public boolean holdsTheUnit() {
        return !LEFT_THE_ORDER.contains(item.getStatus()) && order.getStatus() != OrderStatus.Cancelled;
    }

    public boolean withTheCustomer() {
        return order.getStatus() == OrderStatus.Delivered || order.getStatus() == OrderStatus.Completed;
    }
}
