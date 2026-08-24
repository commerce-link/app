package pl.commercelink.inventory.deliveries;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Confirms that a dropship supplier delivered the selected order items straight to the customer.
 * No goods-in document is produced: the goods never enter the store's warehouse.
 */
@Component
@RequiredArgsConstructor
public class DropshipDeliveryCompletion {

    static final String DROPSHIP_DELIVERY_CONFIRMED_EVENT = "DROPSHIP_DELIVERY_CONFIRMED";

    private final DeliveriesRepository deliveriesRepository;
    private final OrdersRepository ordersRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final OrderLifecycle orderLifecycle;

    public void confirmDelivered(String storeId, Delivery delivery,
                                 List<Allocation> selectedOrderAllocations,
                                 List<Allocation> remainingAllocations) {
        Map<String, Set<String>> itemIdsByOrderId = selectedOrderAllocations.stream()
                .filter(allocation -> allocation.getType() == AllocationType.Order)
                .collect(Collectors.groupingBy(allocation -> allocation.getKey().getOrderId(),
                        Collectors.mapping(allocation -> allocation.getKey().getItemId(), Collectors.toSet())));

        itemIdsByOrderId.forEach((orderId, itemIds) ->
                confirmOrderItems(storeId, delivery.getDeliveryId(), orderId, itemIds));

        if (remainingAllocations.stream().noneMatch(Allocation::isInAllocation)) {
            delivery.markAsReceived();
        }
        delivery.addEvent(new Event(EventType.action, DROPSHIP_DELIVERY_CONFIRMED_EVENT, LocalDateTime.now()));
        deliveriesRepository.save(delivery);
    }

    private void confirmOrderItems(String storeId, String deliveryId, String orderId, Set<String> itemIds) {
        Order order = ordersRepository.findById(storeId, orderId);
        if (order == null) {
            System.err.println("[Dropship] order " + orderId + " not found in store " + storeId
                    + " while confirming dropship delivery " + deliveryId + " - items were not marked as delivered");
            return;
        }
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(orderId);
        for (OrderItem orderItem : orderItems) {
            if (itemIds.contains(orderItem.getItemId())
                    && deliveryId.equals(orderItem.getDeliveryId())
                    && orderItem.isWaitingForCollection()) {
                orderItem.markAsReceived();
                orderItemsRepository.save(orderItem);
            }
        }
        orderLifecycle.update(order, orderItems);
    }
}
