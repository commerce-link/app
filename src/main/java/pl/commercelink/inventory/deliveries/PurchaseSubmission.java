package pl.commercelink.inventory.deliveries;

public record PurchaseSubmission(String deliveryId, boolean awaitingApproval) {
}
