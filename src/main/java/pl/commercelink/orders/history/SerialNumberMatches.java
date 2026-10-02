package pl.commercelink.orders.history;

import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.rma.RMAItem;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.util.List;

public record SerialNumberMatches(List<OrderItem> orderItems, List<RMAItem> rmaItems, List<WarehouseItemView> warehouseItems) {

    public boolean isEmpty() {
        return orderItems.isEmpty() && rmaItems.isEmpty() && warehouseItems.isEmpty();
    }
}
