package pl.commercelink.demo;

import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.event.OrderEvent;

import java.util.List;
import java.util.Map;

record SimOrders(List<Order> orders, Map<String, List<OrderItem>> itemsByOrderId, List<OrderEvent> events) {
}
