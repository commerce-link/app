package pl.commercelink.web.deliveries.create;

import pl.commercelink.inventory.deliveries.DropshipRejection;

import java.util.Locale;

/** Message key of the reason an order cannot be dropshipped: NO_SHIPPING_DETAILS -> orders.dropship.rejected.noShippingDetails. */
public final class DropshipRejectionMessages {

    private DropshipRejectionMessages() {
    }

    public static String keyFor(DropshipRejection rejection) {
        String[] parts = rejection.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder key = new StringBuilder("orders.dropship.rejected.").append(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            key.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return key.toString();
    }
}
