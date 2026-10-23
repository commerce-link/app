package pl.commercelink.shipping;

import pl.commercelink.shipping.api.ShippingException;

/**
 * Another request marked a cancellation of the same shipment a moment ago and its result is not known yet: a second
 * cancel command must not be sent while the first may still succeed.
 */
public class ShipmentCancellationInProgressException extends ShippingException {

    public ShipmentCancellationInProgressException() {
        super(ShipmentCancelService.ALREADY_IN_PROGRESS);
    }
}
