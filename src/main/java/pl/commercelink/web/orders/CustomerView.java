package pl.commercelink.web.orders;

import pl.commercelink.orders.Order;
import pl.commercelink.orders.Shipment;

import java.util.Locale;

/**
 * {@code shippingEmail} and {@code shippingPhone} are set only under "same as billing": the recipient's own contact
 * when it differs from the buyer's (a gift or a courier contact), which the collapsed block would otherwise hide.
 */
public record CustomerView(AddressBlock billing, AddressBlock shipping, boolean shippingSameAsBilling,
                           String shippingEmail, String shippingPhone, String shipmentTypeKey, String pickupPoint, String billingEditHref, String billingLockedKey,
                           String shippingEditHref, String shippingLockedKey) {

    public static CustomerView of(Order order, boolean readOnly, Locale locale) {
        AddressBlock billing = AddressBlock.of(order.getBillingDetails(), locale);
        AddressBlock shipping = AddressBlock.of(order.getShippingDetails(), locale);
        String base = "/dashboard/orders/" + order.getOrderId() + "/address?type=";
        String billingLockedKey = lockedKey(order, true);
        String shippingLockedKey = lockedKey(order, false);
        boolean sameAsBilling = !shipping.isEmpty() && shipping.sameAs(billing);
        return new CustomerView(billing, shipping, sameAsBilling,
                sameAsBilling ? differing(shipping.email(), billing.email()) : null,
                sameAsBilling ? differing(shipping.phone(), billing.phone()) : null,
                OrderLabels.shipmentType(order.firstShipment().map(Shipment::getType).orElse(null)),
                order.firstShipment().map(Shipment::getCollectionPointCode).orElse(null),
                readOnly || billingLockedKey != null ? null : base + "billing",
                readOnly ? null : billingLockedKey,
                readOnly || shippingLockedKey != null ? null : base + "shipping",
                readOnly ? null : shippingLockedKey);
    }

    private static String differing(String shippingValue, String billingValue) {
        return shippingValue != null && !shippingValue.equalsIgnoreCase(billingValue) ? shippingValue : null;
    }

    /**
     * The operator's reason an address can no longer change, or null when it can — the single rule the customer card
     * and the address page (OrdersController.showAddressDetails) both read, so they never disagree.
     */
    public static String lockedKey(Order order, boolean billing) {
        if (billing) {
            return order.isInvoiced() ? "order.customer.billing.locked" : null;
        }
        return order.hasShippingLabel() ? "order.customer.shipping.locked.label" : null;
    }
}
