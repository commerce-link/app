package pl.commercelink.inventory.deliveries;

import pl.commercelink.orders.Order;

import java.util.List;
import java.util.Map;

/** The allocations of a store's open orders together with those orders, so the caller never reads them again. */
public record OrderAllocations(List<Allocation> allocations, Map<String, Order> orders) {

    public OrderAllocations {
        allocations = List.copyOf(allocations);
        orders = Map.copyOf(orders);
    }
}
