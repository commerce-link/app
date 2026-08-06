package pl.commercelink.inventory.deliveries;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class DeliveryOrderedQtyUpdateService {

    @Autowired
    private DeliveriesQueryService deliveriesQueryService;
    @Autowired
    private DeliveriesRepository deliveriesRepository;
    @Autowired
    private WarehouseAllocationsManager warehouseAllocationsManager;

    public OperationResult<Void> run(String storeId, String deliveryId, String mfn, int requestedQty) {
        Delivery delivery = deliveriesQueryService.fetchDeliveryWithAllocations(storeId, deliveryId);
        if (delivery.hasBeenReceived() || !delivery.getDocuments().isEmpty()) {
            return OperationResult.failure("error.message.delivery.qty.not.editable");
        }

        DeliveryItem item = delivery.getItems().stream()
                .filter(i -> StringUtils.equals(i.getMfn(), mfn))
                .findFirst()
                .orElse(null);
        if (item == null) {
            return OperationResult.failure("error.message.delivery.qty.item.not.found");
        }

        if (requestedQty < 1 || requestedQty < item.getMinOrderedQty()) {
            return OperationResult.failure("error.message.delivery.qty.below.min");
        }

        int delta = requestedQty - item.getOrderedQty();
        if (delta == 0) {
            return OperationResult.success();
        }

        double costChange = delta > 0
                ? increaseWarehouseStock(storeId, delivery, item, delta)
                : -decreaseWarehouseStock(storeId, item, -delta);

        delivery.increaseTotalCost(costChange);
        delivery.addEvent(new Event(EventType.action, "DELIVERY_ITEM_QTY_UPDATED", LocalDateTime.now()));
        deliveriesRepository.save(delivery);

        return OperationResult.success();
    }

    private double increaseWarehouseStock(String storeId, Delivery delivery, DeliveryItem item, int amount) {
        Allocation target = adjustableAllocations(item).stream().findFirst().orElse(null);
        if (target == null) {
            warehouseAllocationsManager.createOrdered(storeId, delivery.getDeliveryId(), delivery.getProvider(), item, amount);
            return amount * item.getUnitCost();
        }

        warehouseAllocationsManager.increaseQty(storeId, target.getKey().getItemId(), amount);
        return amount * target.getUnitCost();
    }

    private double decreaseWarehouseStock(String storeId, DeliveryItem item, int amount) {
        double removedCost = 0;
        int remaining = amount;
        for (Allocation allocation : adjustableAllocations(item)) {
            if (remaining == 0) {
                break;
            }
            int removed = warehouseAllocationsManager.decreaseQty(storeId, allocation.getKey().getItemId(), remaining);
            remaining -= removed;
            removedCost += removed * allocation.getUnitCost();
        }
        return removedCost;
    }

    private List<Allocation> adjustableAllocations(DeliveryItem item) {
        return item.getWarehouseAllocations().stream()
                .filter(Allocation::isInAllocation)
                .toList();
    }
}
