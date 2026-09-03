package pl.commercelink.inventory.deliveries;

public class DropshipOrderPendingException extends RuntimeException {

    public DropshipOrderPendingException(String deliveryId, int attempt) {
        super("Dropship order id not yet visible for delivery " + deliveryId + " (attempt " + attempt + ")");
    }
}
