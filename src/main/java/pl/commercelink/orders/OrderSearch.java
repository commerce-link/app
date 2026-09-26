package pl.commercelink.orders;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/** Case-insensitive "contains" over the identifiers, e-mails and names of an order (spec §8.2). Plain text, no regex. */
public final class OrderSearch {

    private OrderSearch() {
    }

    public static String normalize(String q) {
        return q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean matches(Order order, String q) {
        String needle = normalize(q);
        if (needle.isEmpty()) {
            return true;
        }
        ShippingDetails shipping = order.getShippingDetails();
        BillingDetails billing = order.getBillingDetails();
        return Stream.of(
                        order.getOrderId(),
                        order.getExternalOrderId(),
                        billing == null ? null : billing.getEmail(),
                        billing == null ? null : billing.getName(),
                        billing == null ? null : billing.getSurname(),
                        billing == null ? null : billing.getCompanyName(),
                        shipping == null ? null : shipping.getEmail(),
                        shipping == null ? null : shipping.getName(),
                        shipping == null ? null : shipping.getSurname(),
                        shipping == null ? null : shipping.getCompanyName())
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(needle));
    }
}
