package pl.commercelink.orders;

import java.util.Optional;

/**
 * Customer-facing progress stage shown on the public order status page. Collapses internal statuses the
 * customer has no use for (Blocked, Realization) into the neighbouring stage; Cancelled and Completed are
 * not stages at all — Cancelled is rendered as a banner, Completed orders have no public page.
 */
public enum ClientOrderStage {
    Accepted,
    Assembly,
    Preparation,
    Shipping,
    Delivered;

    public static Optional<ClientOrderStage> from(OrderStatus status) {
        return Optional.ofNullable(switch (status) {
            case New, Blocked -> Accepted;
            case Assembly -> Assembly;
            case Assembled, Realization -> Preparation;
            case Shipping -> Shipping;
            case Delivered, Completed -> Delivered;
            case Cancelled -> null;
        });
    }

    public boolean isReachedBy(ClientOrderStage current) {
        return current != null && (current.ordinal() > ordinal() || current == this && isLast());
    }

    private boolean isLast() {
        return ordinal() == values().length - 1;
    }
}
