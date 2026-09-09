package pl.commercelink.orders;

/**
 * Customer-facing fulfilment status of an order item. New and Allocation are deliberately indistinguishable:
 * the store sometimes advances an order without ordering the goods yet (to postpone it for internal reasons),
 * and the customer must not be able to tell that from the page.
 */
public enum ClientOrderItemStatus {
    Allocation,
    AwaitingDelivery,
    Assembled,
    InClaim,
    Returned;

    public static ClientOrderItemStatus from(FulfilmentStatus status) {
        return switch (status) {
            case New, Allocation -> Allocation;
            case Ordered -> AwaitingDelivery;
            case Reserved, Delivered -> Assembled;
            case InRMA, InExternalService -> InClaim;
            case Returned, Replaced, Destroyed -> Returned;
        };
    }
}
