package pl.commercelink.web.payments;

import pl.commercelink.orders.Order;
import pl.commercelink.web.orders.OrderListQuery;

final class OrderListPath {

    private OrderListPath() {
    }

    static String of(Order order) {
        return OrderListQuery.PATH + "/" + order.getOrderId();
    }
}
