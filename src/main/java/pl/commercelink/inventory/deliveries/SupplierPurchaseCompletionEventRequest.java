package pl.commercelink.inventory.deliveries;

public class SupplierPurchaseCompletionEventRequest {

    private String storeId;
    private String deliveryId;
    private String purchaseRef;
    private String orderId;

    public SupplierPurchaseCompletionEventRequest() {
    }

    public SupplierPurchaseCompletionEventRequest(String storeId, String deliveryId, String purchaseRef,
                                                  String orderId) {
        this.storeId = storeId;
        this.deliveryId = deliveryId;
        this.purchaseRef = purchaseRef;
        this.orderId = orderId;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(String deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getPurchaseRef() {
        return purchaseRef;
    }

    public void setPurchaseRef(String purchaseRef) {
        this.purchaseRef = purchaseRef;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
}
