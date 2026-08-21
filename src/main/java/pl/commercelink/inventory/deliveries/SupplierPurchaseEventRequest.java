package pl.commercelink.inventory.deliveries;

public class SupplierPurchaseEventRequest {

    private String storeId;
    private String deliveryId;
    private String provider;
    private String purchaseRef;
    private String orderId;

    public SupplierPurchaseEventRequest() {
    }

    public SupplierPurchaseEventRequest(String storeId, String deliveryId, String provider, String purchaseRef) {
        this(storeId, deliveryId, provider, purchaseRef, null);
    }

    public SupplierPurchaseEventRequest(String storeId, String deliveryId, String provider, String purchaseRef,
                                         String orderId) {
        this.storeId = storeId;
        this.deliveryId = deliveryId;
        this.provider = provider;
        this.purchaseRef = purchaseRef;
        this.orderId = orderId;
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

    public String getOrderId() {
        return orderId;
    }
}
