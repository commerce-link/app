package pl.commercelink.orders.history;

import pl.commercelink.inventory.deliveries.Delivery;

import java.time.LocalDateTime;
import java.util.Comparator;

public record ItemHistoryEvent(Type type, LocalDateTime at, OrderLine orderLine, RmaLine rmaLine, Delivery delivery) {

    /** Declaration order breaks ties between events of the same moment, newest first. */
    public enum Type { RMA_CREATED, ORDER_PLACED, DELIVERY_RECEIVED, DELIVERY_ORDERED }

    public static final Comparator<ItemHistoryEvent> NEWEST_FIRST =
            Comparator.comparing(ItemHistoryEvent::at, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
                    .thenComparing(ItemHistoryEvent::type);

    static ItemHistoryEvent orderPlaced(OrderLine line) {
        return new ItemHistoryEvent(Type.ORDER_PLACED, line.placedAt(), line, null, null);
    }

    static ItemHistoryEvent rmaCreated(RmaLine line) {
        return new ItemHistoryEvent(Type.RMA_CREATED, line.rma().getCreatedAt(), null, line, null);
    }

    static ItemHistoryEvent deliveryOrdered(Delivery delivery) {
        return new ItemHistoryEvent(Type.DELIVERY_ORDERED, delivery.getOrderedAt(), null, null, delivery);
    }

    static ItemHistoryEvent deliveryReceived(Delivery delivery) {
        return new ItemHistoryEvent(Type.DELIVERY_RECEIVED, delivery.getReceivedAt(), null, null, delivery);
    }
}
