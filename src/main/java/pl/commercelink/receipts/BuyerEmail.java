package pl.commercelink.receipts;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.Order;
import pl.commercelink.stores.Store;

/**
 * The buyer's e-mail for a receipt. {@code Order.getEmail()} always mirrors {@code getBillingDetails().getEmail()}
 * live (a write-only projection field used for persistence), so the billing address is the only real source. A
 * point-of-sale order's walk-in buyer copies the store's own e-mail ({@code BillingDetails.walkInCustomer}), which is
 * not the customer's: an e-receipt sent there never reaches the buyer, so it counts as no e-mail.
 */
public final class BuyerEmail {

    private BuyerEmail() {
    }

    public static String of(Order order, String storeEmail) {
        String email = order.getBillingDetails() == null ? null : order.getBillingDetails().getEmail();
        if (StringUtils.isBlank(email)) {
            return null;
        }
        String stripped = email.strip();
        if (order.isPointOfSale() && storeEmail != null && stripped.equalsIgnoreCase(storeEmail.strip())) {
            return null;
        }
        return stripped;
    }

    public static String of(Order order, Store store) {
        return of(order, store.getBillingDetails() == null ? null : store.getBillingDetails().getEmail());
    }
}
