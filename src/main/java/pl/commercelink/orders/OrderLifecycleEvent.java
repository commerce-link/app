package pl.commercelink.orders;

public class OrderLifecycleEvent {

    private String storeId;
    private String orderId;
    private OrderLifecycleEventType type;
    private String externalOrderId;
    private String marketplace;

    private OrderLifecycleEvent() {

    }

    public OrderLifecycleEvent(String storeId, String orderId, OrderLifecycleEventType type) {
        this(storeId, orderId, type, null, null);
    }

    public OrderLifecycleEvent(String storeId, String orderId, OrderLifecycleEventType type,
                               String externalOrderId, String marketplace) {
        this.storeId = storeId;
        this.orderId = orderId;
        this.type = type;
        this.externalOrderId = externalOrderId;
        this.marketplace = marketplace;
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

    public String getExternalOrderId() {
        return externalOrderId;
    }

    public String getMarketplace() {
        return marketplace;
    }
}
