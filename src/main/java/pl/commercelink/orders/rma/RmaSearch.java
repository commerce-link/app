package pl.commercelink.orders.rma;

import pl.commercelink.orders.ShippingDetails;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The RMA list search: one phrase, any case, anywhere in the numbers, e-mails, names and company of an RMA. */
public final class RmaSearch {

    private RmaSearch() {
    }

    public static boolean matches(RMA rma, String q) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return true;
        }
        ShippingDetails details = rma.getShippingDetails();
        Stream<String> person = details == null ? Stream.empty() : Stream.of(
                Stream.of(details.getName(), details.getSurname()).filter(Objects::nonNull).collect(Collectors.joining(" ")),
                details.getCompanyName(), details.getEmail());
        return Stream.concat(Stream.of(rma.getRmaId(), rma.getOrderId(), rma.getEmail(), rma.getExternalReturnReference()), person)
                .filter(Objects::nonNull)
                .anyMatch(text -> text.toLowerCase(Locale.ROOT).contains(needle));
    }
}
