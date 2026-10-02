package pl.commercelink.web.payments;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

/** The tiles of the Payments page (spec §4.1); each one counts both sides and narrows both tabs. */
public enum PaymentFocus {
    OVERDUE("overdue"), TODAY("today"), UNDERPAID("underpaid"), REFUND("refund");

    private final String param;

    PaymentFocus(String param) {
        this.param = param;
    }

    public String param() {
        return param;
    }

    public static Optional<PaymentFocus> parse(String value) {
        return value == null ? Optional.empty()
                : Arrays.stream(values()).filter(f -> f.param.equalsIgnoreCase(value.trim())).findFirst();
    }

    public boolean matches(PayableEntry entry, LocalDate today) {
        return switch (this) {
            case OVERDUE -> entry.owes() && entry.due() != null && entry.due().isBefore(today);
            case TODAY -> entry.owes() && today.equals(entry.due());
            case UNDERPAID -> entry.standing() == PaymentsAmounts.Standing.UNDERPAID;
            case REFUND -> entry.standing() == PaymentsAmounts.Standing.REFUND;
        };
    }

    public boolean matches(ReceivableEntry entry) {
        return switch (this) {
            case OVERDUE -> entry.urgency().isBad();
            case TODAY -> entry.urgency() == ReceivableEntry.Urgency.SHIP_TODAY;
            case UNDERPAID -> entry.standing() == PaymentsAmounts.Standing.UNDERPAID;
            case REFUND -> entry.standing() == PaymentsAmounts.Standing.REFUND;
        };
    }
}
