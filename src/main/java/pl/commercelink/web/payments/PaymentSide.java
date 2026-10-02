package pl.commercelink.web.payments;

import java.util.Arrays;
import java.util.Optional;

/** The two tabs of the Payments page: what the store owes its suppliers and what its customers owe it. */
public enum PaymentSide {
    PAYABLES("payables"), RECEIVABLES("receivables");

    private final String param;

    PaymentSide(String param) {
        this.param = param;
    }

    public String param() {
        return param;
    }

    public static Optional<PaymentSide> parse(String value) {
        return value == null ? Optional.empty()
                : Arrays.stream(values()).filter(s -> s.param.equalsIgnoreCase(value.trim())).findFirst();
    }
}
