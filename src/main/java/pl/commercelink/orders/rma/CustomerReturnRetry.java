package pl.commercelink.orders.rma;

import pl.commercelink.orders.Shipment;

/**
 * Whether the operator can book a customer's failed return again: the RMA is open, its return failed to be created,
 * nothing is being created now, and the RMA holds what the customer chose (address and package template; RMAs from
 * before the template was saved have none and are not offered the retry).
 */
public final class CustomerReturnRetry {

    private CustomerReturnRetry() {
    }

    static boolean possible(RMA rma) {
        return rma.getShipments() != null && (rma.getStatus() == null || !rma.getStatus().isClosed())
                && rma.getShipments().stream().anyMatch(s -> isCustomerReturn(s) && s.creationFailed())
                && rma.getShipments().stream().noneMatch(Shipment::isCreating)
                && rma.getReturnPackageTemplateId() != null && rma.getShippingDetails() != null;
    }

    /**
     * The customer books a return without choosing a pickup address (the courier comes to the customer), so its
     * package is never in the store's pickup list.
     */
    public static boolean isCustomerReturn(Shipment s) {
        return s.getProvider() != null && s.getPickUpAddressId() == null;
    }
}
