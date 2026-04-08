package pl.commercelink.orders;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.invoicing.InvoiceCreationEventPublisher;
import pl.commercelink.orders.notifications.OrderNotificationsEventPublisher;
import pl.commercelink.warehouse.GoodsOutEventPublisher;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.starter.security.model.CustomUser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
public class OrderLifecycle {

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

    public void update(Order order) {
        update(order, null);
    }

    public void update(Order order, List<OrderItem> orderItems) {

        OrderStatus previousOrderStatus = order.getStatus();

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

            if (order.isAwaitingInvoiceGeneration() || order.isAwaitingDocumentsGeneration()) {
                String createdBy = CustomSecurityContext.getLoggedInUser()
                        .map(CustomUser::getName)
                        .orElse("System");
                goodsOutEventPublisher.publish(order, createdBy);
            }

            boolean hasAllOrderItemsReturned = getOrFetchOrderItems(order.getOrderId(), orderItems).stream().allMatch(OrderItem::isReturned);
            if (hasAllOrderItemsReturned) {
                order.setStatus(OrderStatus.Completed);

                if (order.getReview().getStatus() == OrderReviewStatus.ToBeCollected) {
                    order.getReview().setStatus(OrderReviewStatus.NotApplicable);
                }
            }
        }

        if (order.isInReviewForMoreThan(3)) {
            OrderReview review = order.getReview();

            if (order.isSettled()) {
                review.setStatus(OrderReviewStatus.NoResponse);
                order.setStatus(OrderStatus.Completed);
            }
        }

        if (order.getReview().hasOneOfStatuses(OrderReviewStatus.NotApplicable, OrderReviewStatus.NoResponse, OrderReviewStatus.Positive, OrderReviewStatus.Negative)) {
            if (order.isSettled()) {
                order.setStatus(OrderStatus.Completed);
            }
        }

        // Save the updated order back to the database
        ordersRepository.save(order);

        notificationEventPublisher.publish(order);

        if (previousOrderStatus != order.getStatus()) {
            orderLifecycleEventPublisher.publish(order, OrderLifecycleEventType.StatusChange);
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
}
