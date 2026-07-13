package pl.commercelink.demo;

import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;

import java.util.List;
import java.util.Map;

record DemoOrders(List<Order> orders, Map<String, List<OrderItem>> itemsByOrderId, Delivery delivery) {
}
