package pl.commercelink.inventory.supplier;

public class SupplierFeedTargetMissingException extends RuntimeException {

    public SupplierFeedTargetMissingException(String storeId, String supplierName, String reason) {
        super("Feed of supplier " + supplierName + " cannot be imported for store " + storeId + ": " + reason);
    }
}
