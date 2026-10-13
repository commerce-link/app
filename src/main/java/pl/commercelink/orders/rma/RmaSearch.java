package pl.commercelink.orders.rma;

import pl.commercelink.orders.ShippingDetails;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The RMA list search: one phrase, any case and with or without Polish diacritics ("zielinska" finds "Zielińska"),
 * anywhere in the numbers, e-mails, names and company of an RMA.
 */
public final class RmaSearch {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private RmaSearch() {
    }

    public static boolean matches(RMA rma, String q) {
        String needle = q == null ? "" : fold(q.trim());
        if (needle.isEmpty()) {
            return true;
        }
        ShippingDetails details = rma.getShippingDetails();
        Stream<String> person = details == null ? Stream.empty() : Stream.of(
                Stream.of(details.getName(), details.getSurname()).filter(Objects::nonNull).collect(Collectors.joining(" ")),
                details.getCompanyName(), details.getEmail());
        return Stream.concat(Stream.of(rma.getRmaId(), rma.getOrderId(), rma.getEmail(), rma.getExternalReturnReference()), person)
                .filter(Objects::nonNull)
                .anyMatch(text -> fold(text).contains(needle));
    }

    // ł has no decomposition in Unicode, so it is mapped by hand; the other Polish letters lose their marks in NFD
    static String fold(String text) {
        String lower = text.toLowerCase(Locale.ROOT).replace('ł', 'l');
        return MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
    }
}
