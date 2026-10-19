package pl.commercelink.web.payments;

/**
 * Amounts on the Payments page: a rest below half a grosz is rounding noise, not money owed (spec D2).
 */
public final class PaymentsAmounts {

    public static final double EPSILON = 0.005;

    public enum Standing { UNPAID, UNDERPAID, COD, REFUND }

    private PaymentsAmounts() {
    }

    public static boolean isZero(double amount) {
        return Math.abs(amount) < EPSILON;
    }
}
