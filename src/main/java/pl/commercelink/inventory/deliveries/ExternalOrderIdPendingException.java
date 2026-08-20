package pl.commercelink.inventory.deliveries;

public class ExternalOrderIdPendingException extends RuntimeException {

    public ExternalOrderIdPendingException(String deliveryId, int attempt) {
        super("External order id for delivery " + deliveryId + " still pending after attempt " + attempt);
    }
}
