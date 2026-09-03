package pl.commercelink.web.dtos;

import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;

import java.util.List;

public record OrderStatusSelection(List<OrderStatus> statuses, List<String> selected) {

    private static final List<OrderStatus> DEFAULT_ORDER =
            List.of(OrderStatus.Assembled, OrderStatus.Assembly, OrderStatus.New);

    public static OrderStatusSelection resolve(List<Order> orders, List<String> requested, boolean showAll) {
        if (showAll) {
            return new OrderStatusSelection(List.of(), List.of());
        }
        if (requested != null && !requested.isEmpty()) {
            return new OrderStatusSelection(requested.stream().map(OrderStatus::valueOf).toList(), requested);
        }
        OrderStatus fallback = DEFAULT_ORDER.stream()
                .filter(status -> orders.stream().anyMatch(order -> order.getStatus() == status))
                .findFirst()
                .orElse(OrderStatus.New);
        return new OrderStatusSelection(List.of(fallback), List.of(fallback.name()));
    }

    public List<Order> narrow(List<Order> orders) {
        return statuses.isEmpty()
                ? orders
                : orders.stream().filter(order -> statuses.contains(order.getStatus())).toList();
    }
}
