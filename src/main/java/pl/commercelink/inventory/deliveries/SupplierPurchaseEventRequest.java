package pl.commercelink.inventory.deliveries;

public class SupplierPurchaseEventRequest {

    private String storeId;
    private String deliveryId;
    private String provider;
    private String purchaseRef;
    // Defaults to 0 so old in-flight SQS messages (serialized before this field existed) still deserialize.
    private int attempt;

    public SupplierPurchaseEventRequest() {
    }

    public SupplierPurchaseEventRequest(String storeId, String deliveryId, String provider, String purchaseRef) {
        this.storeId = storeId;
        this.deliveryId = deliveryId;
        this.provider = provider;
        this.purchaseRef = purchaseRef;
    }

    public SupplierPurchaseEventRequest(String storeId, String deliveryId, String provider, String purchaseRef, int attempt) {
        this.storeId = storeId;
        this.deliveryId = deliveryId;
        this.provider = provider;
        this.purchaseRef = purchaseRef;
        this.attempt = attempt;
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

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }
}
