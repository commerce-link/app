package pl.commercelink.inventory.deliveries;

public class SupplierConfirmationPendingException extends RuntimeException {

    public SupplierConfirmationPendingException(String deliveryId, int attempt) {
        super("Supplier confirmation for delivery " + deliveryId + " still pending after attempt " + attempt);
    }
}
