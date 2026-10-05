package pl.commercelink.orders.history;

import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Where the unit is now (spec §5.4). A reservation for an order deletes the warehouse row and moves the number onto
 * the order item, and a row with qty above one can keep listing a number already taken, so an order that holds the
 * unit wins over the warehouse.
 */
public record ItemNow(State state, OrderLine orderLine, RmaLine rmaLine, WarehouseItemView warehouseItem) {

    public enum State { IN_RMA, IN_ORDER, AT_CUSTOMER, IN_STOCK, RESERVED, INBOUND, WAREHOUSE_OTHER, UNKNOWN }

    private static final List<FulfilmentStatus> WAREHOUSE_PREFERENCE =
            List.of(FulfilmentStatus.Delivered, FulfilmentStatus.Reserved, FulfilmentStatus.Ordered);

    public static ItemNow resolve(List<OrderLine> orders, List<RmaLine> rmas, List<WarehouseItemView> warehouse) {
        Optional<RmaLine> rma = rmas.stream().filter(RmaLine::inProgress)
                .max(Comparator.comparing((RmaLine l) -> l.rma().getCreatedAt(), Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder())));
        if (rma.isPresent()) {
            return new ItemNow(State.IN_RMA, null, rma.get(), null);
        }
        Optional<OrderLine> order = orders.stream().filter(OrderLine::holdsTheUnit)
                .max(Comparator.comparing(OrderLine::placedAt, Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder())));
        if (order.isPresent()) {
            return new ItemNow(order.get().withTheCustomer() ? State.AT_CUSTOMER : State.IN_ORDER, order.get(), null, null);
        }
        return warehouse.stream().min(Comparator.comparingInt(ItemNow::preference))
                .map(item -> new ItemNow(warehouseState(item.getStatus()), null, null, item))
                .orElse(new ItemNow(State.UNKNOWN, null, null, null));
    }

    private static int preference(WarehouseItemView item) {
        int index = WAREHOUSE_PREFERENCE.indexOf(item.getStatus());
        return index < 0 ? WAREHOUSE_PREFERENCE.size() : index;
    }

    private static State warehouseState(FulfilmentStatus status) {
        if (status == null) {
            return State.WAREHOUSE_OTHER;
        }
        return switch (status) {
            case Delivered -> State.IN_STOCK;
            case Reserved -> State.RESERVED;
            case Ordered -> State.INBOUND;
            default -> State.WAREHOUSE_OTHER;
        };
    }
}
