package pl.commercelink.inventory.deliveries;

public class OrderIdRefreshEventRequest {

    private String storeId;
    private String deliveryId;
    private String provider;
    private String purchaseRef;

    public OrderIdRefreshEventRequest() {
    }

    public OrderIdRefreshEventRequest(String storeId, String deliveryId, String provider, String purchaseRef) {
        this.storeId = storeId;
        this.deliveryId = deliveryId;
        this.provider = provider;
        this.purchaseRef = purchaseRef;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public String getProvider() {
        return provider;
    }

    public String getPurchaseRef() {
        return purchaseRef;
    }
}
