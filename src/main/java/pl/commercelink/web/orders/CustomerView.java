package pl.commercelink.web.orders;

import pl.commercelink.orders.Order;
import pl.commercelink.orders.Shipment;

import java.util.Locale;

public record CustomerView(AddressBlock billing, AddressBlock shipping, boolean shippingSameAsBilling,
                           String shipmentTypeKey, String pickupPoint, String billingEditHref, String billingLockedKey,
                           String shippingEditHref, String shippingLockedKey) {

    public static CustomerView of(Order order, boolean readOnly, Locale locale) {
        AddressBlock billing = AddressBlock.of(order.getBillingDetails(), locale);
        AddressBlock shipping = AddressBlock.of(order.getShippingDetails(), locale);
        String base = "/dashboard/orders/" + order.getOrderId() + "/address?type=";
        String billingLockedKey = lockedKey(order, true);
        String shippingLockedKey = lockedKey(order, false);
        return new CustomerView(billing, shipping, !shipping.isEmpty() && shipping.sameAs(billing),
                OrderLabels.shipmentType(order.firstShipment().map(Shipment::getType).orElse(null)),
                order.firstShipment().map(Shipment::getCollectionPointCode).orElse(null),
                readOnly || billingLockedKey != null ? null : base + "billing",
                readOnly ? null : billingLockedKey,
                readOnly || shippingLockedKey != null ? null : base + "shipping",
                readOnly ? null : shippingLockedKey);
    }

    /**
     * The operator's reason an address can no longer change (B3, P17), or null when it can — the single rule the
     * customer card and the address page (OrderDetailsController) both read, so they never disagree.
     */
    public static String lockedKey(Order order, boolean billing) {
        if (billing) {
            return order.isInvoiced() ? "order.customer.billing.locked" : null;
        }
        if (order.canOperatorChangeShippingAddress()) {
            return null;
        }
        return order.hasShippingLabel() ? "order.customer.shipping.locked.label" : "order.customer.shipping.locked.status";
    }
}
