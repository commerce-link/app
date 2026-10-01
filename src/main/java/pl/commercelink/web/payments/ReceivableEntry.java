package pl.commercelink.web.payments;

import pl.commercelink.orders.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An open order whose customer still owes money or is owed a refund (spec §5.2). Urgency follows fulfilment (spec D3):
 * goods that left without the money are urgent; cash on delivery is normal until the order is Delivered.
 */
public record ReceivableEntry(Order order, double unpaid, PaymentsAmounts.Standing standing, PaymentSource method,
                              Urgency urgency, LocalDate shipDate, boolean shipped, boolean delivered) {

    public static final Set<OrderStatus> OPEN_STATUSES = EnumSet.of(OrderStatus.New, OrderStatus.Blocked,
            OrderStatus.Assembly, OrderStatus.Assembled, OrderStatus.Realization, OrderStatus.Shipping, OrderStatus.Delivered);

    public enum Urgency {
        SHIPPED_UNPAID, COD_UNSETTLED, SHIP_OVERDUE, SHIP_TODAY, SHIP_TOMORROW, SHIP_IN_TWO_DAYS, NONE;

        public boolean isBad() {
            return this == SHIPPED_UNPAID || this == COD_UNSETTLED;
        }

        public boolean isWarn() {
            return this == SHIP_OVERDUE || this == SHIP_TODAY || this == SHIP_TOMORROW || this == SHIP_IN_TWO_DAYS;
        }
    }

    public static Optional<ReceivableEntry> of(Order order, LocalDate today) {
        double unpaid = order.getUnpaidAmount();
        if (PaymentsAmounts.isZero(unpaid)) {
            return Optional.empty();
        }
        PaymentSource method = method(order);
        boolean cod = method == PaymentSource.CashOnDelivery;
        boolean shipped = !OrderAttention.isBeforeShipping(order) || order.hasShippedShipment();
        boolean delivered = order.getStatus() == OrderStatus.Delivered;
        double paid = order.getPaidAmount();
        PaymentsAmounts.Standing standing = unpaid < 0 ? PaymentsAmounts.Standing.REFUND
                : paid > PaymentsAmounts.EPSILON ? PaymentsAmounts.Standing.UNDERPAID
                : cod ? PaymentsAmounts.Standing.COD : PaymentsAmounts.Standing.UNPAID;
        LocalDate shipDate = shipped ? lastShipped(order) : order.getShippingDueAt();
        Urgency urgency = unpaid < 0 ? Urgency.NONE : urgency(cod, shipped, delivered, order.getShippingDueAt(), today);
        return Optional.of(new ReceivableEntry(order, unpaid, standing, method, urgency, shipDate, shipped, delivered));
    }

    public boolean owes() {
        return unpaid > 0;
    }

    public double amount() {
        return Math.abs(unpaid);
    }

    private static Urgency urgency(boolean cod, boolean shipped, boolean delivered, LocalDate due, LocalDate today) {
        if (cod) {
            return delivered ? Urgency.COD_UNSETTLED : Urgency.NONE;
        }
        if (shipped) {
            return Urgency.SHIPPED_UNPAID;
        }
        if (due == null) {
            return Urgency.NONE;
        }
        long days = ChronoUnit.DAYS.between(today, due);
        return days < 0 ? Urgency.SHIP_OVERDUE : days == 0 ? Urgency.SHIP_TODAY : days == 1 ? Urgency.SHIP_TOMORROW
                : days == 2 ? Urgency.SHIP_IN_TWO_DAYS : Urgency.NONE;
    }

    /**
     * The method the customer chose: the pending payment's source, else the latest payment's (spec §5.2).
     */
    private static PaymentSource method(Order order) {
        Payment pending = order.getPendingPayment();
        if (pending != null && pending.getSource() != null) {
            return pending.getSource();
        }
        Payment latest = order.getLatestPayment();
        return latest == null ? null : latest.getSource();
    }

    private static LocalDate lastShipped(Order order) {
        return order.getShipments().stream()
                .map(s -> s.getDeliveredAt() != null ? s.getDeliveredAt() : s.getShippedAt())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(LocalDateTime::toLocalDate)
                .orElse(null);
    }
}
