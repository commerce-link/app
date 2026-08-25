package pl.commercelink.inventory.deliveries;

public class DropshipTrackingEventRequest {

    private String storeId;
    private String deliveryId;
    private String externalDeliveryId;

    public DropshipTrackingEventRequest() {
    }

    public DropshipTrackingEventRequest(String storeId, String deliveryId, String externalDeliveryId) {
        this.storeId = storeId;
        this.deliveryId = deliveryId;
        this.externalDeliveryId = externalDeliveryId;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public String getExternalDeliveryId() {
        return externalDeliveryId;
    }
}
