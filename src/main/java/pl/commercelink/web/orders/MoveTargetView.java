package pl.commercelink.web.orders;

import pl.commercelink.orders.Order;

import java.util.Locale;

/** The "move to an existing order" dialog's preview of the order the items would go to. */
public record MoveTargetView(String orderId, String shortId, String clientName, String statusLabel, String statusTone,
                             int items, String amount, boolean canReceiveItems, String reason) {

    // the amount arrives already formatted (general.currency.amount), so JS and templates only print it.
    public static MoveTargetView of(Order target, int items, String statusLabel, String amount, String reason) {
        AddressBlock billing = AddressBlock.of(target.getBillingDetails(), Locale.ROOT);
        String client = billing.name() != null ? billing.name() : billing.company() != null ? billing.company() : billing.email();
        return new MoveTargetView(target.getOrderId(), target.getShortenedOrderId(), client, statusLabel,
                OrderLabels.tone(target.getStatus()), items, amount, reason == null, reason);
    }

    public static MoveTargetView ambiguous(String reason) {
        return new MoveTargetView(null, null, null, null, null, 0, null, false, reason);
    }
}
