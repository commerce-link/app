package pl.commercelink.inventory.deliveries;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.event.Event;
import pl.commercelink.orders.event.EventType;
import pl.commercelink.orders.notifications.OrderNotificationsEventPublisher;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Component
public class DeliveriesManager {

    @Autowired
    private OrdersRepository ordersRepository;
    @Autowired
    private OrderItemsRepository orderItemsRepository;
    @Autowired
    private OrderNotificationsEventPublisher notificationEventPublisher;
    @Autowired
    private DeliveriesRepository deliveriesRepository;
    @Autowired
    private OrderAllocationsManager orderAllocationsManager;
    @Autowired
    private WarehouseAllocationsManager warehouseAllocationsManager;

    public void deleteAllocations(String storeId, String deliveryId, List<Allocation> allocations) {
        List<Allocation> active = allocations.stream().filter(Allocation::isInAllocation).toList();
        if (active.isEmpty()) {
            return;
        }

        for (Allocation allocation : active) {
            if (allocation.getType() == AllocationType.Warehouse) {
                warehouseAllocationsManager.remove(storeId, allocation.getKey().getItemId());
            } else if (allocation.getType() == AllocationType.Order) {
                orderAllocationsManager.remove(storeId, allocation.getKey().getOrderId(), allocation.getKey().getItemId());
            }
        }

        double totalRemovedCost = active.stream().mapToDouble(Allocation::getTotalCost).sum();

        Delivery delivery = deliveriesRepository.findById(storeId, deliveryId);
        delivery.decreaseTotalCost(totalRemovedCost);
        deliveriesRepository.save(delivery);
    }

    public void reassignAllocations(
            String storeId,
            String sourceDeliveryId,
            String targetDeliveryId,
            List<Allocation> orderAllocations,
            List<Allocation> warehouseAllocations
    ) {
        if (sourceDeliveryId.equals(targetDeliveryId) || (orderAllocations.isEmpty() && warehouseAllocations.isEmpty())) {
            return;
        }

        double totalMovedCost = Stream.concat(orderAllocations.stream(), warehouseAllocations.stream())
                .mapToDouble(Allocation::getTotalCost)
                .sum();

        Delivery source = deliveriesRepository.findById(storeId, sourceDeliveryId);
        source.decreaseTotalCost(totalMovedCost);
        deliveriesRepository.save(source);

        Delivery target = deliveriesRepository.findById(storeId, targetDeliveryId);
        target.increaseTotalCost(totalMovedCost);
        deliveriesRepository.save(target);

        orderAllocationsManager.reassign(targetDeliveryId, orderAllocations);
        warehouseAllocationsManager.reassign(storeId, targetDeliveryId, warehouseAllocations);
    }

    public void splitAllocations(
            String storeId,
            String deliveryId,
            String externalDeliveryId,
            LocalDate estimatedDeliveryAt,
            List<Allocation> orderAllocations,
            List<Allocation> warehouseAllocations
    ) {
        if (deliveriesRepository.findByExternalDeliveryId(storeId, externalDeliveryId) != null) {
            throw new IllegalArgumentException("Delivery with externalId " + externalDeliveryId + " already exists. Use merge to move allocations to an existing delivery.");
        }

        var sourceDelivery = deliveriesRepository.findById(storeId, deliveryId);
        var targetDelivery = new Delivery(
                storeId,
                externalDeliveryId,
                sourceDelivery.getProvider(),
                sourceDelivery.getPaymentStatus(),
                estimatedDeliveryAt,
                sourceDelivery.getShippingCost(),
                sourceDelivery.getPaymentCost(),
                sourceDelivery.getPaymentTerms(),
                sourceDelivery.getTax()
        );
        deliveriesRepository.save(targetDelivery);

        reassignAllocations(storeId, deliveryId, targetDelivery.getDeliveryId(), orderAllocations, warehouseAllocations);
    }

    public void updateDelivery(Delivery updatedDelivery) {
        Delivery existingDelivery = deliveriesRepository.findById(updatedDelivery.getStoreId(), updatedDelivery.getDeliveryId());

        boolean isDeliveryDelayed = updatedDelivery.getEstimatedDeliveryAt() != null
                && updatedDelivery.getEstimatedDeliveryAt().isAfter(existingDelivery.getEstimatedDeliveryAt());

        existingDelivery.setEstimatedDeliveryAt(updatedDelivery.getEstimatedDeliveryAt());
        existingDelivery.updateShippingCost(updatedDelivery.getShippingCost());
        existingDelivery.updatePaymentCost(updatedDelivery.getPaymentCost());
        existingDelivery.setTax(updatedDelivery.getTax());
        existingDelivery.setPaymentTerms(updatedDelivery.getPaymentTerms());
        existingDelivery.addEvent(new Event(EventType.action, "DELIVERY_UPDATED", LocalDateTime.now()));

        if (isDeliveryDelayed) {
            updateAssociatedOrdersForDeliveryChange(existingDelivery.getStoreId(), existingDelivery);
            existingDelivery.addEvent(new Event(EventType.action, "DELIVERY_DELAYED", LocalDateTime.now()));
        }

        deliveriesRepository.save(existingDelivery);
    }

    private void updateAssociatedOrdersForDeliveryChange(String storeId, Delivery delivery) {
        List<String> orderIds = orderItemsRepository.findByDeliveryIdAndStatuses(
                delivery.getDeliveryId(), Collections.singletonList(FulfilmentStatus.Ordered));

        orderIds.stream()
                .map(orderId -> ordersRepository.findById(storeId, orderId))
                .filter(order -> !order.hasStatus(OrderStatus.Completed))
                .forEach(order -> {
                    LocalDate oldAssemblyDate = order.getEstimatedAssemblyAt();
                    LocalDate newAssemblyDate = order.updateEstimatedAssemblyAt(
                            delivery.getEstimatedDeliveryAt()
                    );

                    if (oldAssemblyDate != null && !Objects.equals(oldAssemblyDate, newAssemblyDate)) {
                        notificationEventPublisher.publishAssemblyDateChanged(order, oldAssemblyDate);
                    }

                    ordersRepository.save(order);
                });
    }
}
