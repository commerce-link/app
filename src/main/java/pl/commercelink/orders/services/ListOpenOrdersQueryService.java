package pl.commercelink.orders.services;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.filters.model.OrderFilter;
import pl.commercelink.orders.filters.OrderFilterMatcher;

import java.util.Comparator;
import java.util.List;

@Component
public class ListOpenOrdersQueryService {

    private final OrdersRepository ordersRepository;
    private final OrderFilterMatcher orderFilterMatcher;

    public ListOpenOrdersQueryService(OrdersRepository ordersRepository, OrderFilterMatcher orderFilterMatcher) {
        this.ordersRepository = ordersRepository;
        this.orderFilterMatcher = orderFilterMatcher;
    }

    public List<Order> listOpen(String storeId, OrderFilter filter) {
        List<Order> openOrders = ordersRepository.findAllActiveOrders(storeId).stream()
                .filter(order -> order.getStatus() != OrderStatus.Completed && order.getStatus() != OrderStatus.Cancelled)
                .sorted(Comparator.comparing(Order::getEstimatedShippingAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return filter == null ? openOrders : orderFilterMatcher.apply(openOrders, filter);
    }
}
