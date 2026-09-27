package pl.commercelink.web.orders;

import org.springframework.context.MessageSource;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderReviewStatus;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.fulfilment.FulfilmentType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The "Do zamknięcia" strip: exactly the parts of Order.isSettled, each with what is missing. Nothing here
 * decides anything; OrderLifecycle closes the order.
 */
public record OrderClosingChecklist(List<Item> items) {

    /**
     * {@code NOT_APPLICABLE} does not block closing, like {@code DONE}, but it is not ticked: nothing was done, the
     * condition simply does not concern this order (no shipments, reviews not collected, no invoice on a replacement
     * order from a return).
     */
    public enum State { DONE, TODO, NOT_APPLICABLE }

    public record Item(State state, String text, String anchor, boolean reviewDialog) {

        static Item done(String text) {
            return new Item(State.DONE, text, null, false);
        }

        static Item todo(String text, String anchor) {
            return new Item(State.TODO, text, anchor, false);
        }

        static Item reviewTodo(String text) {
            return new Item(State.TODO, text, null, true);
        }

        static Item notApplicable(String text) {
            return new Item(State.NOT_APPLICABLE, text, null, false);
        }

        public boolean done() {
            return state == State.DONE;
        }

        public boolean todo() {
            return state == State.TODO;
        }

        public boolean notApplicable() {
            return state == State.NOT_APPLICABLE;
        }
    }

    public static OrderClosingChecklist of(Order order, boolean goodsIssueRequired, MessageSource messages, Locale locale) {
        List<Item> items = new ArrayList<>();
        List<Shipment> shipments = order.getShipments();
        if (shipments.isEmpty()) {
            // allMatch on an empty list is true: the order counts as delivered, the text says why
            String key = order.getFulfilmentType() == FulfilmentType.DirectToConsumer
                    ? "order.closing.shipments.none.dropship" : "order.closing.shipments.none";
            items.add(Item.notApplicable(text(messages, locale, key)));
        } else if (order.isDelivered()) {
            items.add(Item.done(text(messages, locale, "order.closing.shipments.done")));
        } else {
            long delivered = shipments.stream().filter(s -> s.getDeliveredAt() != null).count();
            items.add(Item.todo(text(messages, locale, "order.closing.shipments.todo", delivered, shipments.size()),
                    "#przesylki"));
        }

        double unpaid = order.getUnpaidAmount();
        if (order.isFullyPaid()) {
            items.add(Item.done(text(messages, locale, "order.closing.paid.done")));
        } else if (unpaid < 0) {
            items.add(Item.todo(text(messages, locale, "order.closing.paid.overpaid", amount(messages, locale, -unpaid)),
                    "#platnosci"));
        } else {
            items.add(Item.todo(text(messages, locale, "order.closing.paid.todo", amount(messages, locale, unpaid)),
                    "#platnosci"));
        }

        if (order.isRMAReplacementOrder()) {
            items.add(Item.notApplicable(text(messages, locale, "order.closing.invoice.rma")));
        } else if (order.isInvoiced()) {
            Document closing = order.getClosingDocument().orElseThrow();
            items.add(Item.done(text(messages, locale, "order.closing.invoice.done",
                    label(messages, locale, closing.getType()), closing.getNumber())));
        } else {
            DocumentType next = order.getNextDocumentToIssue().orElse(order.getReceiptType());
            // "Issue" only offers the invoicing system's documents; a consumer receipt is typed in with "Add document"
            String key = order.getIssuableDocumentTypes().contains(next)
                    ? "order.closing.invoice.todo" : "order.closing.invoice.todo.manual";
            items.add(Item.todo(text(messages, locale, key, label(messages, locale, next)), "#dokumenty"));
        }

        if (goodsIssueRequired) {
            boolean issued = !order.isAwaitingDocumentsGeneration(true);
            items.add(issued
                    ? Item.done(text(messages, locale, "order.closing.goods.issue.done",
                            order.getDocumentByType(DocumentType.GoodsIssue).map(Document::getNumber).orElse("")))
                    : Item.todo(text(messages, locale, "order.closing.goods.issue.todo"), "#dokumenty"));
        }

        if (order.getReview() == null || order.getReview().getStatus() == null
                || order.getReview().getStatus() == OrderReviewStatus.NotApplicable) {
            // a review marked "not applicable" does not concern the order, like one that is not collected at all
            items.add(Item.notApplicable(text(messages, locale, "order.closing.review.none")));
        } else if (!order.isAwaitingReview()) {
            items.add(Item.done(text(messages, locale, "order.closing.review.done",
                    messages.getMessage(OrderLabels.reviewStatus(order.getReview().getStatus()), null, locale))));
        } else {
            items.add(Item.reviewTodo(text(messages, locale, "order.closing.review.todo")));
        }
        return new OrderClosingChecklist(List.copyOf(items));
    }

    public long missing() {
        return items.stream().filter(Item::todo).count();
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

    private static String amount(MessageSource messages, Locale locale, double value) {
        return messages.getMessage("general.currency.amount", new Object[]{Money.format(value)}, locale);
    }

    private static String text(MessageSource messages, Locale locale, String key, Object... args) {
        return messages.getMessage(key, args.length == 0 ? null : args, locale);
    }
}
