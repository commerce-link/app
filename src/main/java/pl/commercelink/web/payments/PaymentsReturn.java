package pl.commercelink.web.payments;

import java.util.Optional;

/**
 * Where a payment saved from the Payments page sends the operator back to (spec §6.1). Only the page's own address is
 * accepted: the value comes from a form field, so anything else would be an open redirect.
 */
public final class PaymentsReturn {

    public static final String NOTICE = "paymentsNotice";
    public static final String ERROR = "paymentsError";

    private PaymentsReturn() {
    }

    public static Optional<String> target(String returnTo) {
        if (returnTo == null) {
            return Optional.empty();
        }
        String target = returnTo.trim();
        boolean own = target.equals(PaymentsQuery.PATH) || target.startsWith(PaymentsQuery.PATH + "?");
        boolean clean = target.chars().noneMatch(c -> c == '\r' || c == '\n');
        return own && clean ? Optional.of(target) : Optional.empty();
    }
}
