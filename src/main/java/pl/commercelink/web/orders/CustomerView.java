package pl.commercelink.web.orders;

import pl.commercelink.orders.Order;
import pl.commercelink.orders.Shipment;

import java.util.Locale;

/**
 * The billing and the shipping block are always shown in full, even when they are the same (the client wants both
 * readable at a glance, without a "same as billing" shortcut). An editable block carries its form: "Edit" opens it
 * in a dialog, and leads to the address page without JavaScript.
 */
public record CustomerView(AddressBlock billing, AddressBlock shipping, String shipmentTypeKey, String pickupPoint,
                           String billingEditHref, String billingLockedKey, String shippingEditHref,
                           String shippingLockedKey, OrderAddressForm billingForm, OrderAddressForm shippingForm) {

    public static CustomerView of(Order order, boolean readOnly, Locale locale) {
        return of(order, readOnly, false, locale);
    }

    /** receiptLocked: an e-receipt is being issued for the order (ReceiptOrderState#locksOrder). */
    public static CustomerView of(Order order, boolean readOnly, boolean receiptLocked, Locale locale) {
        String base = "/dashboard/orders/" + order.getOrderId() + "/address?type=";
        String billingLockedKey = lockedKey(order, true, receiptLocked);
        String shippingLockedKey = lockedKey(order, false);
        return new CustomerView(AddressBlock.of(order.getBillingDetails(), locale),
                AddressBlock.of(order.getShippingDetails(), locale),
                OrderLabels.shipmentType(order.firstShipment().map(Shipment::getType).orElse(null)),
                order.firstShipment().map(Shipment::getCollectionPointCode).orElse(null),
                readOnly || billingLockedKey != null ? null : base + "billing",
                readOnly ? null : billingLockedKey,
                readOnly || shippingLockedKey != null ? null : base + "shipping",
                readOnly ? null : shippingLockedKey,
                // the dialogs of the "Edit" links, filled with the saved address; none where there is no link
                readOnly || billingLockedKey != null ? null : OrderAddressForm.billing(order.getOrderId(), order.getBillingDetails()),
                readOnly || shippingLockedKey != null ? null : OrderAddressForm.shipping(order.getOrderId(), order.getShippingDetails()));
    }

    /**
     * The operator's reason an address can no longer change, or null when it can — the single rule the customer card
     * and the address page (OrdersController.showAddressDetails) both read, so they never disagree.
     */
    public static String lockedKey(Order order, boolean billing) {
        return lockedKey(order, billing, false);
    }

    /**
     * receiptLocked: while an e-receipt is being issued its request (buyer, e-mail) is frozen, and a tax id added now
     * would turn the order into a business one that the invoicing system would invoice a second time — the billing
     * address is fixed as once the invoice is issued. The shipping address is not part of the receipt.
     */
    public static String lockedKey(Order order, boolean billing, boolean receiptLocked) {
        if (billing) {
            return order.isInvoiced() ? "order.customer.billing.locked"
                    : receiptLocked ? "order.customer.billing.locked.receipt" : null;
        }
        return order.hasShippingLabel() ? "order.customer.shipping.locked.label" : null;
    }
}
