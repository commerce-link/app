package pl.commercelink.web.orders;

import org.springframework.context.MessageSource;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderAttention;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.ShippingDetails;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.stream.Stream;

/** Turns an order into the texts of its list row (spec §6). One instance per request, bound to the request locale. */
public class OrderRowMapper {

    private static final DateTimeFormatter SAME_YEAR = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter OTHER_YEAR = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final MessageSource messages;
    private final Locale locale;
    private final DecimalFormat amount;

    public OrderRowMapper(MessageSource messages, Locale locale) {
        this.messages = messages;
        this.locale = locale;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        this.amount = new DecimalFormat("#,##0.00", symbols);
    }

    public OrderRow map(Order order, LocalDate today) {
        ShippingDetails shipping = order.getShippingDetails();
        BillingDetails billing = order.getBillingDetails();
        String email = billing == null ? null : billing.getEmail();
        String name = firstNonBlank(personOrCompany(shipping), personOrCompany(billing));
        LocalDate due = order.getShippingDueAt();
        boolean beforeShipping = OrderAttention.isBeforeShipping(order);
        String dueNote = null;
        String dueTone = "";
        if (due != null && beforeShipping && due.isBefore(today)) {
            long days = ChronoUnit.DAYS.between(due, today);
            // "dni" fits every count but 1 — the one Polish form this page cannot avoid, handled by a second key
            dueNote = days == 1 ? text("orders.list.due.overdue.one") : text("orders.list.due.overdue", days);
            dueTone = "is-bad";
        } else if (due != null && beforeShipping && due.equals(today)) {
            dueNote = text("orders.list.due.today");
            dueTone = "is-warn";
        }
        double unpaid = order.getUnpaidAmount();
        return new OrderRow(
                OrderListQuery.PATH + "/" + order.getOrderId(),
                order.getShortenedOrderId(),
                sourceText(order),
                order.getExternalOrderId() == null || order.getExternalOrderId().isBlank() ? null
                        : text("orders.list.source.external", order.getExternalOrderId()),
                name != null ? name : (email != null ? email : text("orders.list.client.unknown")),
                shipping == null ? null : blankToNull(shipping.getCity()),
                name != null ? email : null,
                due == null ? text("orders.list.due.none")
                        : (due.getYear() == today.getYear() ? SAME_YEAR : OTHER_YEAR).format(due),
                dueNote,
                dueTone,
                order.getStatus() == null ? "" : text("OrderStatus." + order.getStatus().name()),
                statusTone(order.getStatus()),
                money(order.getTotalPrice()),
                unpaid > 0 ? text("orders.list.unpaid", money(unpaid)) : null);
    }

    /** Spec D11. */
    public static String statusTone(OrderStatus status) {
        if (status == null) {
            return "is-neutral";
        }
        return switch (status) {
            case Blocked -> "is-bad";
            case Delivered, Completed -> "is-ok";
            case Cancelled -> "is-neutral";
            default -> "is-info";
        };
    }

    private String sourceText(Order order) {
        OrderSourceType type = order.getSource() == null || order.getSource().getType() == null
                ? OrderSourceType.Other : order.getSource().getType();
        String marketplace = order.getSource() == null ? null : blankToNull(order.getSource().getName());
        if (type == OrderSourceType.Marketplace && marketplace != null) {
            return marketplace;
        }
        return text("order.source.type." + type.name());
    }

    private String money(double value) {
        return text("general.currency.amount", amount.format(value));
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, locale);
    }

    private static String personOrCompany(ShippingDetails details) {
        return details == null ? null : firstNonBlank(details.getCompanyName(), joined(details.getName(), details.getSurname()));
    }

    private static String personOrCompany(BillingDetails details) {
        return details == null ? null : firstNonBlank(details.getCompanyName(), joined(details.getName(), details.getSurname()));
    }

    private static String joined(String a, String b) {
        String joined = Stream.of(a, b).filter(v -> v != null && !v.isBlank()).map(String::trim).reduce((x, y) -> x + " " + y).orElse("");
        return blankToNull(joined);
    }

    private static String firstNonBlank(String a, String b) {
        return blankToNull(a) != null ? a.trim() : blankToNull(b);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
