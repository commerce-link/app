package pl.commercelink.orders;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.inventory.deliveries.DropshipItemLookup;
import pl.commercelink.invoicing.InvoiceCreationEventPublisher;
import pl.commercelink.orders.notifications.OrderNotificationsEventPublisher;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.security.model.CustomUser;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.warehouse.GoodsOutEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class OrderLifecycle {

    @Autowired
    private StoresRepository storesRepository;
    @Autowired
    private OrdersRepository ordersRepository;
    @Autowired
    private OrderItemsRepository orderItemsRepository;
    @Autowired
    private OrderLifecycleEventPublisher orderLifecycleEventPublisher;
    @Autowired
    private OrderNotificationsEventPublisher notificationEventPublisher;
    @Autowired
    private DeliveriesRepository deliveriesRepository;
    @Autowired
    private InvoiceCreationEventPublisher invoiceCreationEventPublisher;
    @Autowired
    private GoodsOutEventPublisher goodsOutEventPublisher;
    @Autowired
    private DropshipItemLookup dropshipItemLookup;

    public void update(Order order) {
        update(order, null);
    }

    public void update(Order order, List<OrderItem> orderItems) {

        if (order.getStatus() == OrderStatus.Cancelled) {
            return;
        }

        OrderStatus previousOrderStatus = order.getStatus();

        Store store = storesRepository.findById(order.getStoreId());
        boolean documentsGenerationEnabled = store != null && store.hasDocumentsGenerationEnabled();
        // The item fetch + dropship lookup below is only relevant once the order can possibly be Delivered
        // (either already is, or has just reached that point earlier in this same update). Every other status
        // short-circuits before paying for it, keeping this out of the hot path for New/Assembly/Shipping etc.
        boolean warehouseDocumentsRequired = documentsGenerationEnabled
                && (order.getStatus() == OrderStatus.Delivered || order.isDelivered())
                && warehouseDocumentsRequired(order, orderItems);

        if (order.getStatus() == OrderStatus.New || order.getStatus() == OrderStatus.Assembly) {
            orderItems = getOrFetchOrderItems(order.getOrderId(), orderItems);

            boolean hasAllOrderItemsOrdered = !orderItems.isEmpty() && orderItems.stream().allMatch(OrderItem::isOrdered);
            boolean hasAllOrderItemsDelivered = !orderItems.isEmpty() && orderItems.stream().allMatch(OrderItem::isDelivered);

            if (hasAllOrderItemsDelivered) {
                order.setStatus(OrderStatus.Assembled);
                order.updateEstimatedAssemblyAt(LocalDate.now());
            } else if (hasAllOrderItemsOrdered) {
                order.setStatus(OrderStatus.Assembly);
                if (order.getEstimatedAssemblyAt() == null) {
                    order.updateEstimatedAssemblyAt(calculateEstimatedDeliveryDate(order, orderItems));
                }
            }
        }

        if (order.getStatus() == OrderStatus.Assembled || order.getStatus() == OrderStatus.Realization) {
            if (order.hasBeenShippedOrIsReadyForCollection()) {
                order.setStatus(OrderStatus.Shipping);
            }
        }

        if (order.getStatus() == OrderStatus.Shipping) {
            order.getShipments().stream()
                    .filter(shipment -> shipment.getType() == ShipmentType.PersonalCollection)
                    .filter(shipment -> shipment.getShippedAt() == null)
                    .forEach(shipment -> {
                        shipment.setShippedAt(LocalDateTime.now());
                    });
        }

        if (order.getStatus() == OrderStatus.Shipping && order.getShipments().stream().allMatch(s -> s.getDeliveredAt() != null)) {
            order.setStatus(OrderStatus.Delivered);
        }

        if (order.getStatus() == OrderStatus.Delivered) {
            order.getShipments().stream()
                    .filter(shipment -> shipment.getType() == ShipmentType.PersonalCollection)
                    .forEach(shipment -> {
                        if (shipment.getShippedAt() == null) {
                            shipment.setShippedAt(LocalDateTime.now());
                        }
                        if (shipment.getDeliveredAt() == null) {
                            shipment.setDeliveredAt(LocalDateTime.now());
                        }
                    });

            if (order.isAwaitingInvoiceGeneration() || order.isAwaitingDocumentsGeneration(warehouseDocumentsRequired)) {
                String createdBy = CustomSecurityContext.getLoggedInUser()
                        .map(CustomUser::getName)
                        .orElse("System");
                goodsOutEventPublisher.publish(order, createdBy);
            }

            boolean hasAllOrderItemsReturned = getOrFetchOrderItems(order.getOrderId(), orderItems).stream().allMatch(OrderItem::isReturned);
            if (hasAllOrderItemsReturned) {
                order.setStatus(OrderStatus.Cancelled);

                if (order.getReview().getStatus() == OrderReviewStatus.ToBeCollected) {
                    order.getReview().setStatus(OrderReviewStatus.NotApplicable);
                }
            }
        }

        if (order.isSettled(warehouseDocumentsRequired)) {
            order.setStatus(OrderStatus.Completed);
        }

        // Save the updated order back to the database
        ordersRepository.save(order);

        notificationEventPublisher.publish(order);

        publishLifecycleEvents(order, previousOrderStatus);
    }

    private void publishLifecycleEvents(Order order, OrderStatus previousOrderStatus) {
        if (previousOrderStatus == order.getStatus()) {
            return;
        }

        if (order.getStatus() == OrderStatus.Cancelled) {
            orderLifecycleEventPublisher.publish(order, OrderLifecycleEventType.OrderCancelled);
        } else if (previousOrderStatus == OrderStatus.New || previousOrderStatus == OrderStatus.Blocked) {
            orderLifecycleEventPublisher.publish(order, OrderLifecycleEventType.OrderAccepted);
        }
        if (order.getStatus() == OrderStatus.Completed) {
            orderLifecycleEventPublisher.publish(order, OrderLifecycleEventType.OrderCompleted);
        }
    }

    private LocalDate calculateEstimatedDeliveryDate(Order order, List<OrderItem> orderItems) {
        List<Delivery> deliveries = orderItems.stream()
                .map(OrderItem::getDeliveryId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .map(deliveryId -> deliveriesRepository.findById(order.getStoreId(), deliveryId))
                .filter(Objects::nonNull)
                .toList();

        return deliveries.stream()
                .map(Delivery::getEstimatedDeliveryAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private List<OrderItem> getOrFetchOrderItems(String orderId, @Nullable List<OrderItem> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            return orderItemsRepository.findByOrderId(orderId);
        }
        return orderItems;
    }

    // Dropship goods never reach the warehouse, so they can never produce a goods issue note. Demanding one
    // would keep such an order open forever. The item fetch is lazy: it only runs when the answer is not
    // already settled by an existing document. Callers only reach here once the cheaper preconditions
    // (store flag on, order delivered) already hold.
    private boolean warehouseDocumentsRequired(Order order, List<OrderItem> orderItems) {
        if (order.getDocumentByType(DocumentType.GoodsIssue).isPresent()) {
            return true;
        }
        return hasWarehouseFulfilledProductItems(order, orderItems);
    }

    private boolean hasWarehouseFulfilledProductItems(Order order, List<OrderItem> orderItems) {
        List<OrderItem> items = getOrFetchOrderItems(order.getOrderId(), orderItems);
        Set<String> dropshipItemIds = dropshipItemLookup.itemIdsInDropshipDeliveries(order.getStoreId(), items);
        return items.stream()
                .filter(OrderItem::isProduct)
                .anyMatch(item -> !dropshipItemIds.contains(item.getItemId()));
    }
}
