package pl.commercelink.orders.fulfilment;

/**
 * An order item waiting for a supplier that no offer covers on the selection page (spec D7): it stays in the queue,
 * and the page lists it so the operator sees it instead of an order that silently looks complete. NARROWED when one
 * of the queue's narrowing options was on (it may have removed the offer), NO_OFFER otherwise — which also covers
 * offers without enough stock when several orders are selected together.
 */
public record UnmatchedItem(String orderId, String itemId, String name, int qty, double price, Reason reason) {

    public enum Reason {
        NO_OFFER,
        NARROWED
    }
}
