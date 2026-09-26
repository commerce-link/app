package pl.commercelink.web.orders;

import org.springframework.context.MessageSource;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.Shipment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The "To close" card (spec B1, §5.2.2): exactly the parts of Order.isSettled, each with what is missing. Nothing here
 * decides anything; OrderLifecycle closes the order.
 */
public record OrderClosingChecklist(List<Item> items) {

    public record Item(boolean done, String text, String anchor, boolean reviewDialog) {
    }

    public static OrderClosingChecklist of(Order order, boolean goodsIssueRequired, MessageSource messages, Locale locale) {
        List<Item> items = new ArrayList<>();
        List<Shipment> shipments = order.getShipments();
        if (shipments.isEmpty()) {
            // allMatch on an empty list is true: the order counts as delivered, the text says why
            items.add(new Item(true, text(messages, locale, "order.closing.shipments.none"), null, false));
        } else if (order.isDelivered()) {
            items.add(new Item(true, text(messages, locale, "order.closing.shipments.done"), null, false));
        } else {
            long delivered = shipments.stream().filter(s -> s.getDeliveredAt() != null).count();
            items.add(new Item(false, text(messages, locale, "order.closing.shipments.todo", delivered, shipments.size()),
                    "#przesylki", false));
        }

        double unpaid = order.getUnpaidAmount();
        if (order.isFullyPaid()) {
            items.add(new Item(true, text(messages, locale, "order.closing.paid.done"), null, false));
        } else if (unpaid < 0) {
            items.add(new Item(false, text(messages, locale, "order.closing.paid.overpaid", Money.format(-unpaid)),
                    "#platnosci", false));
        } else {
            items.add(new Item(false, text(messages, locale, "order.closing.paid.todo", Money.format(unpaid)),
                    "#platnosci", false));
        }

        if (order.isRMAReplacementOrder()) {
            items.add(new Item(true, text(messages, locale, "order.closing.invoice.rma"), null, false));
        } else if (order.isInvoiced()) {
            Document closing = order.getClosingDocument().orElseThrow();
            items.add(new Item(true, text(messages, locale, "order.closing.invoice.done",
                    label(messages, locale, closing.getType()), closing.getNumber()), null, false));
        } else {
            DocumentType next = order.getNextDocumentToIssue().orElse(order.getReceiptType());
            items.add(new Item(false, text(messages, locale, "order.closing.invoice.todo", label(messages, locale, next)),
                    "#dokumenty", false));
        }

        if (goodsIssueRequired) {
            boolean issued = !order.isAwaitingDocumentsGeneration(true);
            items.add(issued
                    ? new Item(true, text(messages, locale, "order.closing.goods.issue.done",
                            order.getDocumentByType(DocumentType.GoodsIssue).map(Document::getNumber).orElse("")), null, false)
                    : new Item(false, text(messages, locale, "order.closing.goods.issue.todo"), "#dokumenty", false));
        }

        if (order.getReview() == null || order.getReview().getStatus() == null) {
            items.add(new Item(true, text(messages, locale, "order.closing.review.none"), null, false));
        } else if (!order.isAwaitingReview()) {
            items.add(new Item(true, text(messages, locale, "order.closing.review.done",
                    messages.getMessage(OrderLabels.reviewStatus(order.getReview().getStatus()), null, locale)), null, false));
        } else {
            items.add(new Item(false, text(messages, locale, "order.closing.review.todo"), null, true));
        }
        return new OrderClosingChecklist(List.copyOf(items));
    }

    public long missing() {
        return items.stream().filter(item -> !item.done()).count();
    }

    public boolean allDone() {
        return missing() == 0;
    }

    public String title(MessageSource messages, Locale locale) {
        long missing = missing();
        return missing == 0
                ? messages.getMessage("order.closing.title.ready", null, locale)
                : messages.getMessage("order.closing.title.missing", new Object[]{missing}, locale);
    }

    private static String label(MessageSource messages, Locale locale, DocumentType type) {
        return messages.getMessage(OrderLabels.documentType(type), null, locale);
    }

    private static String text(MessageSource messages, Locale locale, String key, Object... args) {
        return messages.getMessage(key, args.length == 0 ? null : args, locale);
    }
}
