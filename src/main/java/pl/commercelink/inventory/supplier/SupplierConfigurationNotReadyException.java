package pl.commercelink.inventory.supplier;

public class SupplierConfigurationNotReadyException extends RuntimeException {

    public SupplierConfigurationNotReadyException(String storeId, String supplierName) {
        super("Configuration for supplier " + supplierName + " of store " + storeId + " is not readable yet");
    }
}
