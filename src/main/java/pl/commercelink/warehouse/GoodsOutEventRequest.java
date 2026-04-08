package pl.commercelink.warehouse;

public class GoodsOutEventRequest {

    private String storeId;
    private String orderId;
    private String createdBy;

    public GoodsOutEventRequest() {
    }

    public GoodsOutEventRequest(String storeId, String orderId, String createdBy) {
        this.storeId = storeId;
        this.orderId = orderId;
        this.createdBy = createdBy;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
