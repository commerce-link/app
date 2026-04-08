package pl.commercelink.inventory.deliveries;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.notifications.OrderNotificationsEventPublisher;
import pl.commercelink.warehouse.builtin.WarehouseAllocationsManager;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    public void deleteAllocation(String storeId, Allocation allocation) {
        if (!allocation.isInAllocation()) {
            return;
        }

        if (allocation.getType() == AllocationType.Warehouse) {
            warehouseAllocationsManager.remove(storeId, allocation.getKey().getItemId()); ;
        } else if (allocation.getType() == AllocationType.Order) {
            String orderId = allocation.getKey().getOrderId();
            String itemId = allocation.getKey().getItemId();
            orderAllocationsManager.remove(storeId, orderId, itemId);
        }
    }

    public void reassignAllocations(
            String storeId,
            String targetDeliveryId,
            List<Allocation> orderAllocations,
            List<Allocation> warehouseAllocations
    ) {
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
                sourceDelivery.getPaymentTerms()
        );
        deliveriesRepository.save(targetDelivery);

        reassignAllocations(storeId, targetDelivery.getDeliveryId(), orderAllocations, warehouseAllocations);
    }

    public void updateAssociatedOrdersForDeliveryChange(String storeId, Delivery delivery) {
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
