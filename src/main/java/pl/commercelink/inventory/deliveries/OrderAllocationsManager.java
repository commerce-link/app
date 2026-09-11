package pl.commercelink.inventory.deliveries;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class OrderAllocationsManager {

    @Autowired
    private OrdersRepository ordersRepository;
    @Autowired
    private OrderItemsRepository orderItemsRepository;
    @Autowired
    private OrdersManager ordersManager;

    public List<Allocation> fetchAll(String storeId) {
        List<Allocation> allocations = new LinkedList<>();

        List<Order> activeOrders = ordersRepository.findAllByStoreIdAndStatus(storeId, OrderStatus.New, OrderStatus.Assembly);

        for (Order order : activeOrders) {
            List<Allocation> orderAllocations = orderItemsRepository.findByOrderIdAndStatus(order.getOrderId(), FulfilmentStatus.Allocation)
                    .stream()
                    // an allocation claimed by a pending delivery is already being bought - it must not be offered again
                    .filter(i -> !i.isClaimed())
                    .map(i -> Allocation.fromOrderItem(order, i))
                    .toList();
            allocations.addAll(orderAllocations);
        }

        return allocations;
    }

    public List<Allocation> fetchAll(String storeId, String deliveryId) {
        Map<String, List<OrderItem>> orderItemsByOrderId = orderItemsRepository.findByDeliveryId(deliveryId)
                .stream()
                .filter(i -> !i.isReplacedOrReturned())
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        List<Allocation> allocations = new LinkedList<>();
        for (String orderId : orderItemsByOrderId.keySet()) {
            Order order = ordersRepository.findById(storeId, orderId);
            for (OrderItem orderItem : orderItemsByOrderId.get(orderId)) {
                allocations.add(Allocation.fromOrderItem(order, orderItem));
            }
        }
        return allocations;
    }

    public void commit(String storeId, String deliveryId, LocalDate estimatedDeliveryAt, List<DeliveryItem> items) {
        selectedOrderAllocations(items).forEach((orderId, costs) ->
                ordersManager.markOrderItemsAsOrdered(storeId, orderId, deliveryId, costs, estimatedDeliveryAt));
    }

    /** Reserves the allocations for a pending delivery: bound to it, still in allocation. */
    public void claim(String storeId, String deliveryId, List<DeliveryItem> items) {
        selectedOrderAllocations(items).forEach((orderId, costs) ->
                ordersManager.claimOrderItems(storeId, orderId, deliveryId, costs));
    }

    private Map<String, Map<String, Double>> selectedOrderAllocations(List<DeliveryItem> items) {
        Map<String, Map<String, Double>> allocationsByOrderId = new HashMap<>();
        for (DeliveryItem item : items) {
            for (Allocation allocation : item.getSelectedAllocations(AllocationType.Order)) {
                allocationsByOrderId
                        .computeIfAbsent(allocation.getKey().getOrderId(), k -> new HashMap<>())
                        .put(allocation.getKey().getItemId(), item.getUnitCost());
            }
        }
        return allocationsByOrderId;
    }

    /**
     * The supplier confirmed the purchase: every item claimed by this delivery becomes ordered and its
     * order receives the confirmed date.
     *
     * <p>The date may be missing: a confirmation without one still orders the items, because the purchase
     * did happen. The assembly-date update is a no-op for a null date.
     */
    public void markClaimedAsOrdered(String storeId, String deliveryId, LocalDate estimatedDeliveryAt) {
        Map<String, Map<String, Double>> claimedByOrderId = new HashMap<>();
        for (OrderItem item : orderItemsRepository.findByDeliveryId(deliveryId)) {
            if (item.isClaimed()) {
                claimedByOrderId
                        .computeIfAbsent(item.getOrderId(), k -> new HashMap<>())
                        .put(item.getItemId(), item.getCost());
            }
        }

        claimedByOrderId.forEach((orderId, costs) -> {
            try {
                ordersManager.markOrderItemsAsOrdered(storeId, orderId, deliveryId, costs, estimatedDeliveryAt);
            } catch (RuntimeException e) {
                log.error("Claimed items not marked as ordered: store={} delivery={} order={} estimatedDeliveryAt={}",
                        storeId, deliveryId, orderId, estimatedDeliveryAt, e);
            }
        });
    }

    public void release(String storeId, String deliveryId, String provider) {
        Map<String, List<String>> itemIdsByOrderId = orderItemsRepository.findByDeliveryId(deliveryId)
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId,
                        Collectors.mapping(OrderItem::getItemId, Collectors.toList())));

        itemIdsByOrderId.forEach((orderId, itemIds) ->
                ordersManager.returnOrderItemsToSupplierAllocation(storeId, orderId, deliveryId, provider, itemIds));
    }

    public double updateUnitCosts(String deliveryId, Map<String, Double> unitCostsByMfn) {
        double delta = 0;
        for (OrderItem item : orderItemsRepository.findByDeliveryId(deliveryId)) {
            if (item.isReturned() || !unitCostsByMfn.containsKey(item.getManufacturerCode())) {
                continue;
            }
            double itemDelta = item.updateCost(unitCostsByMfn.get(item.getManufacturerCode()));
            if (itemDelta == 0) {
                continue;
            }
            if (!item.isReplacedOrReturned()) {
                delta += itemDelta;
            }
            orderItemsRepository.save(item);
        }
        return delta;
    }

    /** Whether the item is currently reserved by a pending delivery's supplier purchase. */
    public boolean isClaimed(String orderId, String itemId) {
        OrderItem orderItem = orderItemsRepository.findById(orderId, itemId);
        return orderItem != null && orderItem.isClaimed();
    }

    public boolean updateFulfilment(String storeId, String provider, String orderId, String itemId, String ean, String mfn, double unitCost) {
        Order order = ordersRepository.findById(storeId, orderId);
        if (order == null) {
            return false;
        }

        OrderItem orderItem = orderItemsRepository.findById(orderId, itemId);
        if (orderItem == null || !orderItem.updateFulfilment(provider, ean, mfn, unitCost)) {
            return false;
        }

        orderItemsRepository.save(orderItem);
        return true;
    }

    public void reassign(String targetDeliveryId, List<Allocation> allocations) {
        for (Allocation allocation : allocations) {
            OrderItem orderItem = orderItemsRepository.findById(allocation.getKey().getOrderId(), allocation.getKey().getItemId());
            orderItem.setDeliveryId(targetDeliveryId);
            orderItem.setClaimedDeliveryId(targetDeliveryId);
            orderItemsRepository.save(orderItem);
        }
    }

    public boolean remove(String storeId, String orderId, String itemId) {
        return remove(storeId, orderId, Collections.singletonList(itemId));
    }

    public boolean remove(String storeId, String orderId, List<String> orderItemIds) {
        return remove(storeId, orderId, orderItemIds, null);
    }

    public boolean remove(String storeId, String orderId, String itemId, String releasingDeliveryId) {
        return remove(storeId, orderId, Collections.singletonList(itemId), releasingDeliveryId);
    }

    /**
     * Gives the items back to the order: fulfilment cleared, order back to New.
     *
     * @param releasingDeliveryId the delivery that is allowed to give its own claimed items back, or null
     *                            when the caller is not a delivery. An item claimed by any other delivery is
     *                            already being bought there and is left alone.
     * @return whether anything was actually removed
     */
    public boolean remove(String storeId, String orderId, List<String> orderItemIds, String releasingDeliveryId) {
        boolean removed = false;

        for (String orderItemId : orderItemIds) {
            OrderItem orderItem = orderItemsRepository.findById(orderId, orderItemId);
            if (!orderItem.isInAllocationOrOrdered()) {
                continue;
            }
            // an item claimed by a pending delivery is already being bought at the supplier
            if (orderItem.isClaimed() && !orderItem.getClaimedDeliveryId().equals(releasingDeliveryId)) {
                continue;
            }
            orderItem.removeFulfilment();
            orderItemsRepository.save(orderItem);
            removed = true;
        }

        if (removed) {
            Order order = ordersRepository.findById(storeId, orderId);
            order.setStatus(OrderStatus.New);
            ordersRepository.save(order);
        }

        return removed;
    }

}
