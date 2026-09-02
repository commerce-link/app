package pl.commercelink.orders;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.filters.OrderFilter;
import pl.commercelink.orders.filters.OrderFilterMatcher;

import java.util.Comparator;
import java.util.List;

@Component
public class ListOpenOrdersHandler {

    private final OrdersRepository ordersRepository;
    private final OrderFilterMatcher orderFilterMatcher;

    public ListOpenOrdersHandler(OrdersRepository ordersRepository, OrderFilterMatcher orderFilterMatcher) {
        this.ordersRepository = ordersRepository;
        this.orderFilterMatcher = orderFilterMatcher;
    }

    public List<Order> handle(String storeId, OrderFilter filter) {
        List<Order> openOrders = ordersRepository.findAllActiveOrders(storeId).stream()
                .filter(order -> order.getStatus() != OrderStatus.Completed && order.getStatus() != OrderStatus.Cancelled)
                .sorted(Comparator.comparing(Order::getEstimatedShippingAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return filter == null ? openOrders : orderFilterMatcher.apply(openOrders, filter);
    }
}
