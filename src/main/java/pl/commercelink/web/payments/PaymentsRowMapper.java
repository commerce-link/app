package pl.commercelink.web.payments;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.supplier.SupplierLabelMap;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.*;
import pl.commercelink.web.orders.OrderPageModelFactory;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

/** Builds the rows of both tabs; one instance per request, bound to the request locale (like DeliveryRowMapper). */
public class PaymentsRowMapper {

    private static final DateTimeFormatter SAME_YEAR = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter OTHER_YEAR = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final MessageSource messages;
    private final Locale locale;
    private final SupplierLabelMap labels;
    private final DecimalFormat amount;

    public PaymentsRowMapper(MessageSource messages, Locale locale, SupplierLabelMap labels) {
        this.messages = messages;
        this.locale = locale;
        this.labels = labels;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
        // Non-breaking, like web/orders/Money: a number must never wrap in the middle.
        symbols.setGroupingSeparator('\u00A0');
        symbols.setDecimalSeparator(',');
        this.amount = new DecimalFormat("#,##0.00", symbols);
    }

    public PayableRow map(PayableEntry entry, LocalDate today) {
        Delivery d = entry.delivery();
        boolean refund = entry.standing() == PaymentsAmounts.Standing.REFUND;
        String dueNote = null;
        String dueTone = "";
        if (entry.owes() && entry.due() != null && !entry.due().isAfter(today)) {
            long days = ChronoUnit.DAYS.between(entry.due(), today);
            dueNote = days == 0 ? text("deliveries.list.due.today")
                    : days == 1 ? text("deliveries.list.due.overdue.one") : text("deliveries.list.due.overdue", days);
            dueTone = days == 0 ? "is-warn" : "is-bad";
        }
        String external = StringUtils.trimToNull(d.getExternalDeliveryId());
        boolean paidOf = entry.standing() != PaymentsAmounts.Standing.UNPAID;
        return new PayableRow(
                "/dashboard/deliveries/details?deliveryId=" + d.getDeliveryId(),
                d.getShortenedDeliveryId(),
                d.isDropship(),
                d.getOrderedAt() == null ? null : text("payments.row.ordered", format(d.getOrderedAt().toLocalDate(), today)),
                supplierLabel(d),
                external == null ? text("payments.row.external.none") : text("payments.row.external", external),
                entry.due() == null ? text("payments.row.due.none") : format(entry.due(), today),
                dueNote, dueTone,
                stateLabel(entry.standing(), "payable"), stateTone(entry.standing()),
                d.isInvoiced(),
                text(d.isInvoiced() ? "deliveries.list.mark.invoice.done" : "deliveries.list.mark.invoice.todo"),
                money(entry.amount()),
                paidOf ? text("payments.row.paidOf", plain(d.getPaidAmount()), plain(d.getTotalCostGross()))
                        : text("payments.row.net", money(Math.abs(d.getUnpaidAmountNet()))),
                paidOf, refund,
                d.getDeliveryId(),
                expected(entry.unpaid()),
                pending(d.getPendingPayment()),
                text(refund ? "payments.action.refund" : "payments.action.payment"));
    }

    public ReceivableRow map(ReceivableEntry entry, LocalDate today) {
        Order o = entry.order();
        boolean refund = entry.standing() == PaymentsAmounts.Standing.REFUND;
        boolean paidOf = entry.standing() == PaymentsAmounts.Standing.UNDERPAID || refund;
        String email = o.getBillingDetails() == null ? StringUtils.trimToNull(o.getEmail())
                : StringUtils.trimToNull(o.getBillingDetails().getEmail());
        String shipNoteKey = switch (entry.urgency()) {
            case SHIPPED_UNPAID -> "payments.ship.note.shippedUnpaid";
            case COD_UNSETTLED -> "payments.ship.note.codUnsettled";
            case SHIP_OVERDUE -> "payments.ship.note.overdue";
            case SHIP_TODAY -> "payments.ship.note.today";
            case SHIP_TOMORROW -> "payments.ship.note.tomorrow";
            case SHIP_IN_TWO_DAYS -> "payments.ship.note.twoDays";
            case NONE -> null;
        };
        return new ReceivableRow(
                OrderListPath.of(o),
                o.getShortenedOrderId(),
                sourceText(o),
                Objects.requireNonNullElse(OrderPageModelFactory.clientName(o), text("orders.list.client.unknown")),
                email,
                shipText(entry, today),
                shipNoteKey == null ? null : text(shipNoteKey),
                entry.urgency().isBad() ? "is-bad" : entry.urgency().isWarn() ? "is-warn" : "",
                entry.method() == null ? "" : text("PaymentSource." + entry.method().name()),
                stateLabel(entry.standing(), "receivable"), stateTone(entry.standing()),
                money(entry.amount()),
                paidOf ? text("payments.row.paidOf", plain(o.getPaidAmount()), plain(o.getTotalPrice()))
                        : text("payments.row.net", money(Math.abs(o.getUnpaidAmountNet()))),
                paidOf, refund,
                o.getOrderId(),
                expected(entry.amount()),
                pending(o.getPendingPayment()),
                text(refund ? "payments.action.refund" : "payments.action.payment"));
    }

    public String money(double value) {
        return text("general.currency.amount", amount.format(value)).replace(' ', '\u00A0');
    }

    /** A provider without a connection was typed in by hand (the same rule as the deliveries list). */
    public String supplierLabel(Delivery delivery) {
        String provider = delivery.getProvider();
        boolean typed = provider != null && !SupplierRegistry.WAREHOUSE.equals(provider)
                && !labels.has(delivery.getStoreId(), provider);
        return typed ? text("deliveries.list.supplier.typed", provider) : labels.of(delivery.getStoreId(), provider);
    }

    private String shipText(ReceivableEntry entry, LocalDate today) {
        if (entry.shipped()) {
            if (entry.shipDate() == null) {
                return text("payments.ship.gone");
            }
            return text(entry.delivered() ? "payments.ship.delivered" : "payments.ship.shipped", format(entry.shipDate(), today));
        }
        return entry.shipDate() == null ? text("payments.ship.none") : format(entry.shipDate(), today);
    }

    private String sourceText(Order order) {
        OrderSourceType type = order.getSource() == null || order.getSource().getType() == null
                ? OrderSourceType.Other : order.getSource().getType();
        String marketplace = order.getSource() == null ? null : StringUtils.trimToNull(order.getSource().getName());
        String base = type == OrderSourceType.Marketplace && marketplace != null ? marketplace : text("order.source.type." + type.name());
        String external = StringUtils.trimToNull(order.getExternalOrderId());
        return external == null ? base : base + " · " + text("orders.list.source.external", external);
    }

    private String stateLabel(PaymentsAmounts.Standing standing, String side) {
        return text(switch (standing) {
            case UNPAID -> "payments.state.unpaid." + side;
            case UNDERPAID -> "payments.state.underpaid";
            case COD -> "payments.state.cod";
            case REFUND -> "payments.state.refund." + side;
        });
    }

    private static String stateTone(PaymentsAmounts.Standing standing) {
        return switch (standing) {
            case UNDERPAID -> "is-warn";
            case REFUND -> "is-info";
            default -> "is-neutral";
        };
    }

    private static PayableRow.PendingData pending(Payment p) {
        if (p == null) {
            return new PayableRow.PendingData("", "", "", "0", "", "");
        }
        return new PayableRow.PendingData(p.getSource() == null ? "" : p.getSource().name(),
                StringUtils.defaultString(p.getName()), StringUtils.defaultString(p.getReferenceNo()),
                String.valueOf(p.getFee()), StringUtils.defaultString(p.getBankTransactionNo()),
                p.getBankTransactionDate() == null ? "" : p.getBankTransactionDate().toString());
    }

    /**
     * The dialog reads data-unpaid with parseFloat, so it gets a dot and no grouping. A delivery refund is typed negative
     * (deliveryDetails.html does the same); an order refund is passed positive because the server negates it.
     */
    private static String expected(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String plain(double value) {
        return amount.format(value);
    }

    private static String format(LocalDate date, LocalDate today) {
        return (date.getYear() == today.getYear() ? SAME_YEAR : OTHER_YEAR).format(date);
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }
}
