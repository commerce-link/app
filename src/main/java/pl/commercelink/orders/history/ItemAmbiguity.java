package pl.commercelink.orders.history;

import pl.commercelink.orders.OrderStatus;

import java.util.List;

/**
 * A number that cannot be one unit: different products under it, or two orders holding the unit at the same time
 * (spec D8). A sale after a return is the normal path and is not ambiguous.
 */
public record ItemAmbiguity(boolean ambiguous, int orderCount, int productCount) {

    public static ItemAmbiguity of(List<OrderLine> orders, ItemIdentity identity) {
        long openHolding = orders.stream()
                .filter(OrderLine::holdsTheUnit)
                .filter(l -> l.order().getStatus() != OrderStatus.Completed)
                .map(l -> l.order().getOrderId()).distinct().count();
        int orderCount = (int) orders.stream().map(l -> l.order().getOrderId()).distinct().count();
        int productCount = identity == null ? 0 : identity.productCount();
        return new ItemAmbiguity(productCount > 1 || openHolding >= 2, orderCount, productCount);
    }
}
