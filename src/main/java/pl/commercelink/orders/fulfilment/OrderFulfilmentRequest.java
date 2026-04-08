package pl.commercelink.orders.fulfilment;

public class OrderFulfilmentRequest {

    private String storeId;
    private String orderId;

    public OrderFulfilmentRequest() {
    }

    public OrderFulfilmentRequest(String storeId, String orderId) {
        this.storeId = storeId;
        this.orderId = orderId;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getOrderId() {
        return orderId;
    }
}
