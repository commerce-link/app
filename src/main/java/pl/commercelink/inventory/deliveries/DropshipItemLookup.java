package pl.commercelink.inventory.deliveries;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.OrderItem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tells which order items sit in a dropship delivery. Such items never reach the warehouse, so every action that
 * would move them there (return to stock, RMA shortcut, quantity edits) must leave them alone.
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
}
