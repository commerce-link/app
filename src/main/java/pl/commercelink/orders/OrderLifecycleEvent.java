package pl.commercelink.orders;

public class OrderLifecycleEvent {

    private String storeId;
    private String orderId;
    private OrderLifecycleEventType type;

    private OrderLifecycleEvent() {

    }

    public OrderLifecycleEvent(String storeId, String orderId, OrderLifecycleEventType type) {
        this.storeId = storeId;
        this.orderId = orderId;
        this.type = type;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getOrderId() {
        return orderId;
    }

    public OrderLifecycleEventType getType() {
        return type;
    }
}
