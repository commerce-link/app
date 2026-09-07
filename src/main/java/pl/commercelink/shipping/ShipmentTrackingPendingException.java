package pl.commercelink.shipping;

public class ShipmentTrackingPendingException extends RuntimeException {

    public ShipmentTrackingPendingException(String trackingNo, int attempt) {
        super("Tracking subscription for " + trackingNo + " still pending after attempt " + attempt);
    }
}
