package pl.commercelink.web.payments;

import pl.commercelink.inventory.deliveries.Delivery;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A delivery the store still has to settle with its supplier (spec §5.1). A failed or not yet approved supplier order
 * and a delivery that costs nothing are not money owed (spec D2), whatever their paid flag says.
 */
public record PayableEntry(Delivery delivery, double unpaid, PaymentsAmounts.Standing standing, LocalDate due) {

    public static Optional<PayableEntry> of(Delivery delivery) {
        if (delivery.isOrderFailed() || delivery.isAwaitingApproval()
                || PaymentsAmounts.isZero(delivery.getTotalCostGross())) {
            return Optional.empty();
        }
        double unpaid = delivery.getUnpaidAmount();
        if (PaymentsAmounts.isZero(unpaid)) {
            return Optional.empty();
        }
        PaymentsAmounts.Standing standing = unpaid < 0 ? PaymentsAmounts.Standing.REFUND
                : delivery.getPaidAmount() > PaymentsAmounts.EPSILON ? PaymentsAmounts.Standing.UNDERPAID
                : PaymentsAmounts.Standing.UNPAID;
        return Optional.of(new PayableEntry(delivery, unpaid, standing, delivery.getPaymentDueDate()));
    }

    public boolean owes() {
        return unpaid > 0;
    }

    public double amount() {
        return Math.abs(unpaid);
    }
}
