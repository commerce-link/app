package pl.commercelink.inventory.deliveries;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.OrderItem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads how an order's goods travel off the deliveries behind its items - per item and for the order as a
 * whole. Items sitting in a dropship delivery never reach the warehouse, so every action that would move
 * them there (return to stock, RMA shortcut, quantity edits) must leave them alone; and an order whose every
 * leg is a dropship one never sees in-house handling at all.
 */
@Component
@RequiredArgsConstructor
public class DropshipItemLookup {

    private final DeliveriesRepository deliveriesRepository;

    public Set<String> itemIdsInDropshipDeliveries(String storeId, List<OrderItem> orderItems) {
        Map<String, Boolean> dropshipByDeliveryId = new HashMap<>();
        Set<String> itemIds = new HashSet<>();
        for (OrderItem item : orderItems) {
            if (!item.isAllocated() || item.getDeliveryId() == null
                    || SupplierRegistry.WAREHOUSE.equalsIgnoreCase(item.getDeliveryId())) {
                continue;
            }
            boolean dropship = dropshipByDeliveryId.computeIfAbsent(item.getDeliveryId(), deliveryId -> {
                Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
                return delivery != null && delivery.isDropship();
            });
            if (dropship) {
                itemIds.add(item.getItemId());
            }
        }
        return itemIds;
    }

    /**
     * Does the whole order travel straight from its suppliers to the customer? Any leg through our warehouse
     * has to be picked, packed and forwarded by hand, which is what the order's realization days pay for, so
     * only an order whose every delivery is a dropship one skips them. The question is deliberately asked of
     * the order and not of a single delivery: one delivery cannot see the other legs, and the order's
     * fulfilment type cannot see the route either - a direct-to-consumer order can still be served from a
     * warehouse delivery.
     */
    public boolean isEntirelyDropship(String storeId, List<OrderItem> orderItems) {
        return isEntirelyDropship(deliveriesOf(storeId, orderItems));
    }

    /**
     * The same answer for a caller that has already loaded the order's deliveries.
     *
     * <p>An order with no resolvable delivery is not a dropship order: nothing proves its goods bypass the
     * warehouse, and an empty {@code allMatch} would silently read as "travels entirely by dropship".
     */
    public boolean isEntirelyDropship(List<Delivery> deliveries) {
        return !deliveries.isEmpty() && deliveries.stream().allMatch(Delivery::isDropship);
    }

    /**
     * The distinct deliveries behind the order's items. Item delivery ids that resolve to nothing - a supplier
     * name carried by an item still in allocation, a deleted delivery - are skipped rather than guessed at.
     */
    public List<Delivery> deliveriesOf(String storeId, List<OrderItem> orderItems) {
        return orderItems.stream()
                .map(OrderItem::getDeliveryId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .map(deliveryId -> deliveriesRepository.findById(storeId, deliveryId))
                .filter(Objects::nonNull)
                .toList();
    }
}
