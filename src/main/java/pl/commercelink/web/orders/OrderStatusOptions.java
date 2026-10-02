package pl.commercelink.web.orders;

import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;

import java.util.Arrays;
import java.util.List;

/**
 * The "Change status" dialog: the manual statuses as today (every direction allowed, Completed and
 * Cancelled only when current), each with the sentence of its effect; Delivered unavailable without shipment data.
 */
public record OrderStatusOptions(List<Option> options, boolean emailNote, String marketplaceName) {

    public record Option(OrderStatus status, String labelKey, String effectKey, boolean current, boolean disabled,
                         String reasonKey) {
    }

    public static OrderStatusOptions of(Order order) {
        List<Option> options = Arrays.stream(OrderStatus.values())
                .filter(status -> (status != OrderStatus.Completed && status != OrderStatus.Cancelled) || order.hasStatus(status))
                .map(status -> {
                    boolean blocked = !order.canTransitionToDelivered(status);
                    return new Option(status, OrderLabels.status(status), "order.status.effect." + status.name(),
                            order.hasStatus(status), blocked, blocked ? "order.status.effect.Delivered.unavailable" : null);
                })
                .toList();
        return new OrderStatusOptions(options, order.isEmailNotificationsEnabled(),
                order.isMarketplaceOrder() ? order.getSource().getName() : null);
    }
}
