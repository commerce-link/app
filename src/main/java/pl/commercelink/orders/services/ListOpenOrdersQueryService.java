package pl.commercelink.orders.services;

import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.filters.model.OrderFilter;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Component
public class ListOpenOrdersQueryService {

    private final OrdersRepository ordersRepository;

    public ListOpenOrdersQueryService(OrdersRepository ordersRepository) {
        this.ordersRepository = ordersRepository;
    }

    public List<Order> listOpen(String storeId, OrderFilter filter) {
        List<Order> openOrders = ordersRepository.findAllActiveOrders(storeId).stream()
                .sorted(Comparator.comparing(Order::getEstimatedShippingAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (filter == null) {
            return openOrders;
        }
        LocalDate today = LocalDate.now();
        return openOrders.stream().filter(order -> filter.matches(order, today)).toList();
    }
}
