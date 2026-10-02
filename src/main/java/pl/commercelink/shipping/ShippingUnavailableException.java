package pl.commercelink.shipping;

/**
 * The store has no courier account to price or book with: no shipping provider is connected, or its adapter is not
 * installed any more. The courier pages show the reason instead of failing on a missing provider.
 */
public class ShippingUnavailableException extends RuntimeException {

    public ShippingUnavailableException(String storeId) {
        super("Store " + storeId + " has no shipping provider connected.");
    }
}
