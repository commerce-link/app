package pl.commercelink.web.dtos;

import pl.commercelink.orders.OrderItem;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class OrderItemsForm {
    private List<OrderItem> orderItems = new ArrayList<>();

    public OrderItemsForm() {}

    public OrderItemsForm(List<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }

    public List<OrderItem> getOrderItems() {
        return orderItems;
    }

    public void setOrderItems(List<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }

    public List<String> getSelectedOrderItemIds() {
        return this.orderItems.stream()
                .filter(OrderItem::isSelected)
                .map(OrderItem::getItemId)
                .collect(Collectors.toList());
    }
}
