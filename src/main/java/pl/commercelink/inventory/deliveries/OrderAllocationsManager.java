package pl.commercelink.inventory.deliveries;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

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
        Map<String, Map<String, Double>> allocationsByOrderId = new HashMap<>();

        for (DeliveryItem item : items) {
            for (Allocation allocation : item.getSelectedAllocations(AllocationType.Order)) {
                String orderId = allocation.getKey().getOrderId();
                String itemId = allocation.getKey().getItemId();
                allocationsByOrderId
                        .computeIfAbsent(orderId, k -> new HashMap<>())
                        .put(itemId, item.getUnitCost());
            }
        }

        for (String orderId : allocationsByOrderId.keySet()) {
            ordersManager.markOrderItemsAsOrdered(storeId, orderId, deliveryId, allocationsByOrderId.get(orderId), estimatedDeliveryAt);
        }
    }

    public void reassign(String targetDeliveryId, List<Allocation> allocations) {
        for (Allocation allocation : allocations) {
            OrderItem orderItem = orderItemsRepository.findById(allocation.getKey().getOrderId(), allocation.getKey().getItemId());
            orderItem.setDeliveryId(targetDeliveryId);
            orderItemsRepository.save(orderItem);
        }
    }

    public void remove(String storeId, String orderId, String itemId) {
        remove(storeId, orderId, Collections.singletonList(itemId));
    }

    public void remove(String storeId, String orderId, List<String> orderItemIds) {
        boolean removed = false;

        for (String orderItemId : orderItemIds) {
            OrderItem orderItem = orderItemsRepository.findById(orderId, orderItemId);
            if (orderItem.isInAllocationOrOrdered()) {
                orderItem.removeFulfilment();
                orderItemsRepository.save(orderItem);
                removed = true;
            }
        }

        if (removed) {
            Order order = ordersRepository.findById(storeId, orderId);
            order.setStatus(OrderStatus.New);
            ordersRepository.save(order);
        }
    }

}
