package pl.commercelink.inventory.deliveries;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrderLifecycleEventPublisher;
import pl.commercelink.orders.OrderLifecycleEventType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.shipping.ShipmentTrackingSubscriber;
import pl.commercelink.starter.util.OperationResult;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DropshipDeliveryCompletion {

    static final String DROPSHIP_SHIPMENT_CONFIRMED_EVENT = "DROPSHIP_SHIPMENT_CONFIRMED";

    private final DeliveriesRepository deliveriesRepository;
    private final OrdersRepository ordersRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final OrderLifecycle orderLifecycle;
    private final OrderLifecycleEventPublisher orderLifecycleEventPublisher;
    private final ShipmentTrackingSubscriber shipmentTrackingSubscriber;

    public OperationResult<DropshipShipmentResult> confirmShipped(String storeId, Delivery delivery,
                                                                  List<Allocation> selectedOrderAllocations,
                                                                  List<Allocation> remainingAllocations,
                                                                  DropshipShipment shipment) {
        return confirmShipped(storeId, delivery, selectedOrderAllocations, remainingAllocations, shipment, (d, r) -> { });
    }

    public OperationResult<DropshipShipmentResult> confirmShipped(String storeId, Delivery delivery,
                                                                  List<Allocation> selectedOrderAllocations,
                                                                  List<Allocation> remainingAllocations,
                                                                  DropshipShipment shipment,
                                                                  BiConsumer<Delivery, DropshipShipmentResult> beforeSave) {
        Map<String, Set<String>> itemIdsByOrderId = selectedOrderAllocations.stream()
                .filter(allocation -> allocation.getType() == AllocationType.Order)
                .collect(Collectors.groupingBy(allocation -> allocation.getKey().getOrderId(), LinkedHashMap::new,
                        Collectors.mapping(allocation -> allocation.getKey().getItemId(), Collectors.toSet())));

        Map<String, Order> orders = new LinkedHashMap<>();
        for (String orderId : itemIdsByOrderId.keySet()) {
            Order order = ordersRepository.findById(storeId, orderId);
            if (order == null) {
                System.err.println("[Dropship] order " + orderId + " not found in store " + storeId
                        + " while confirming shipment of dropship delivery " + delivery.getDeliveryId());
                continue;
            }
            if (order.getStatus() == OrderStatus.Cancelled) {
                return OperationResult.failure("deliveries.dropship.shipment.error.orderCancelled");
            }
            orders.put(orderId, order);
        }

        int confirmed = 0;
        for (Map.Entry<String, Order> entry : orders.entrySet()) {
            confirmed += confirmOrderItems(delivery.getDeliveryId(), entry.getValue(),
                    itemIdsByOrderId.get(entry.getKey()), shipment);
        }
        if (confirmed == 0) {
            return OperationResult.failure("deliveries.dropship.shipment.error.nothingToConfirm");
        }

        boolean completed = remainingAllocations.stream().noneMatch(Allocation::isInAllocation);
        if (completed) {
            delivery.markAsReceived();
        }
        delivery.addEvent(new Event(EventType.action, DROPSHIP_SHIPMENT_CONFIRMED_EVENT, LocalDateTime.now()));
        DropshipShipmentResult result = completed ? DropshipShipmentResult.COMPLETED : DropshipShipmentResult.PARTIAL;
        beforeSave.accept(delivery, result);
        deliveriesRepository.save(delivery);
        return OperationResult.success(result);
    }

    private int confirmOrderItems(String deliveryId, Order order, Set<String> itemIds, DropshipShipment shipment) {
        List<OrderItem> orderItems = orderItemsRepository.findByOrderId(order.getOrderId());
        int confirmed = 0;
        for (OrderItem orderItem : orderItems) {
            if (itemIds.contains(orderItem.getItemId())
                    && deliveryId.equals(orderItem.getDeliveryId())
                    && orderItem.isWaitingForCollection()) {
                orderItem.markAsReceived();
                orderItemsRepository.save(orderItem);
                confirmed++;
            }
        }
        if (confirmed == 0) {
            return 0;
        }
        Shipment target = shipmentToFill(order);
        boolean firstTrackedParcel = order.getShipments().stream().noneMatch(Shipment::hasShippingData);
        shipment.applyTo(target);
        shipmentTrackingSubscriber.subscribe(order.getStoreId(), order);
        orderLifecycle.update(order, orderItems);
        if (firstTrackedParcel) {
            orderLifecycleEventPublisher.publish(order, OrderLifecycleEventType.ShipmentCreated);
        }
        return confirmed;
    }

    private Shipment shipmentToFill(Order order) {
        return order.getShipments().stream()
                .filter(existing -> existing.getType() != ShipmentType.PersonalCollection && !existing.hasShippingData())
                .findFirst()
                .orElseGet(() -> {
                    Shipment added = new Shipment();
                    order.addShipment(added);
                    return added;
                });
    }
}
