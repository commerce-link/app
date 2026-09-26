package pl.commercelink.web.orders;

import org.springframework.context.MessageSource;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderAttention;
import pl.commercelink.orders.OrderReviewStatus;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.web.orders.OrderRow.DocMark;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/** Turns an order into the texts of its list row (spec §6). One instance per request, bound to the request locale. */
public class OrderRowMapper {

    private static final DateTimeFormatter SAME_YEAR = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter OTHER_YEAR = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final MessageSource messages;
    private final Locale locale;
    private final DecimalFormat amount;
    private final boolean warehouseDocuments;

    /** warehouseDocuments: the store issues warehouse documents (Store.hasDocumentsGenerationEnabled), so a WZ is expected. */
    public OrderRowMapper(MessageSource messages, Locale locale, boolean warehouseDocuments) {
        this.messages = messages;
        this.locale = locale;
        this.warehouseDocuments = warehouseDocuments;
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
                unpaid > 0 ? text("orders.list.unpaid", money(unpaid)) : null,
                marks(order));
    }

    /**
     * The WZ, the closing document and the review, in that order (spec §25): a check for what exists, a to-do mark for
     * what is missing once the order is Delivered — the point where it keeps the order from closing (Order.isSettled).
     * Anything else shows nothing: a gap before delivery is not work yet, and a review already requested (InProgress)
     * no longer blocks the close. The WZ is expected when the store issues warehouse documents and the order is
     * fulfilled from the warehouse — the list does not read the order's items, so a mixed order with dropshipped lines
     * counts as a warehouse one (OrderLifecycle decides from the items).
     */
    List<DocMark> marks(Order order) {
        boolean delivered = order.getStatus() == OrderStatus.Delivered;
        List<DocMark> marks = new ArrayList<>();

        Optional<Document> goodsIssue = order.getDocumentByType(DocumentType.GoodsIssue);
        if (goodsIssue.isPresent()) {
            marks.add(mark("wz", "WZ", "is-done", text("orders.list.mark.done", "WZ", number(goodsIssue.get()))));
        } else if (delivered && warehouseDocuments && order.getFulfilmentType() != FulfilmentType.DirectToConsumer) {
            marks.add(mark("wz", "WZ", "is-todo", text("orders.list.mark.todo", "WZ")));
        }

        Optional<Document> closing = order.getClosingDocument();
        if (closing.isPresent()) {
            DocumentType type = closing.get().getType();
            marks.add(mark("invoice", code(type), "is-done", text("orders.list.mark.done", documentName(type), number(closing.get()))));
        } else if (delivered && !order.isInvoiced()) {
            DocumentType type = order.getReceiptType();
            marks.add(mark("invoice", code(type), "is-todo", text("orders.list.mark.todo", documentName(type))));
        }

        OrderReviewStatus review = order.getReview() == null ? null : order.getReview().getStatus();
        String reviewName = text("orders.list.mark.review");
        if (review == OrderReviewStatus.ToBeCollected && delivered) {
            marks.add(mark("review", "", "is-todo", text("orders.list.mark.review.todo", reviewName)));
        } else if (review == OrderReviewStatus.Positive || review == OrderReviewStatus.Negative || review == OrderReviewStatus.NoResponse) {
            marks.add(mark("review", "", "is-done", text("orders.list.mark.review.done", reviewName, text("OrderReviewStatus." + review.name()))));
        }
        return List.copyOf(marks);
    }

    private static DocMark mark(String kind, String code, String state, String label) {
        return new DocMark(kind, code, state, label);
    }

    /** "PAR" for a receipt, "FV" for every invoice (VAT, final, personal): the code the store's staff already use. */
    private String code(DocumentType type) {
        return text(type == DocumentType.Receipt ? "orders.list.mark.receipt" : "orders.list.mark.invoice");
    }

    private String documentName(DocumentType type) {
        return text("DocumentType." + type.name());
    }

    private static String number(Document document) {
        return document.getNumber() == null ? "" : document.getNumber();
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
