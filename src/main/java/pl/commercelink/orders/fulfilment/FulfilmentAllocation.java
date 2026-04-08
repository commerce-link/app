package pl.commercelink.orders.fulfilment;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.inventory.deliveries.AllocationKey;
import pl.commercelink.orders.OrderItem;

public class FulfilmentAllocation {

    private String orderId;
    private String orderItemId;
    private int orderItemQty;
    private double orderItemPrice;

    public FulfilmentAllocation() {

    }

    public FulfilmentAllocation(OrderItem orderItem) {
        this.orderId = orderItem.getOrderId();
        this.orderItemId = orderItem.getItemId();
        this.orderItemQty = orderItem.getQty();
        this.orderItemPrice = orderItem.getPrice();
    }

    public boolean isFor(OrderItem other) {
        return StringUtils.equals(orderId, other.getOrderId()) && StringUtils.equals(orderItemId, other.getItemId());
    }

    public AllocationKey getKey() {
        return new AllocationKey(orderId, orderItemId, null);
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(String orderItemId) {
        this.orderItemId = orderItemId;
    }

    public int getOrderItemQty() {
        return orderItemQty;
    }

    public void setOrderItemQty(int orderItemQty) {
        this.orderItemQty = orderItemQty;
    }

    public double getOrderItemPrice() {
        return orderItemPrice;
    }

    public void setOrderItemPrice(double orderItemPrice) {
        this.orderItemPrice = orderItemPrice;
    }
}
